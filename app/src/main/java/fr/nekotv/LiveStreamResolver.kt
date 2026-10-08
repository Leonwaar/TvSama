package fr.nekotv

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.webkit.*
import com.streamflixreborn.streamflix.providers.TvSamaAnimeProvider
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Runs the source's player to capture the actual manifest, including request headers. */
@SuppressLint("SetJavaScriptEnabled")
internal suspend fun resolveLiveWebPlayer(context: Context, url: String, referer: String, allowProgressive: Boolean = true): VideoSource =
    withContext(Dispatchers.Main) { coroutineScope {
        var web: WebView? = null
        var container: android.view.ViewGroup? = null
        val probes = mutableListOf<Job>()
        try {
            withTimeout(25_000) {
                suspendCancellableCoroutine { continuation ->
                    val view = WebView(context)
                    web = view
                    // Embedded players need a real viewport and an active WebView lifecycle.
                    val activity = generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }
                        .filterIsInstance<android.app.Activity>().firstOrNull()
                    container = activity?.window?.decorView as? android.view.ViewGroup
                    view.alpha = 0f
                    view.isFocusable = false
                    container?.addView(view, android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT))
                    view.onResume()
                    CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)
                    view.settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        userAgentString = TvSamaAnimeProvider.USER_AGENT
                        javaScriptCanOpenWindowsAutomatically = false
                        setSupportMultipleWindows(true)
                    }
                    view.webChromeClient = WebChromeClient()
                    view.webViewClient = object : WebViewClient() {
                        private val seen = java.util.Collections.synchronizedSet(mutableSetOf<String>())
                        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                            if (continuation.isActive) continuation.resumeWithException(
                                IllegalStateException("Le lecteur web du direct s’est interrompu."))
                            return true
                        }
                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                            if (BuildConfig.DEBUG) android.util.Log.d("TvSamaLive", "Web error ${request.url.host}: ${error.errorCode} ${error.description}")
                        }
                        override fun onPageFinished(view: WebView, page: String) {
                            if (BuildConfig.DEBUG) android.util.Log.d("TvSamaLive", "Page loaded ${Uri.parse(page).host}")
                            view.evaluateJavascript("""(function(){document.querySelectorAll('video').forEach(function(v){v.muted=true;v.play().catch(function(){});});var p=document.querySelector('.vjs-big-play-button,.jw-icon-display,.dplayer-play-icon');if(p)p.click();})();""", null)
                        }
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                            request.url.scheme !in listOf("https", "http") ||
                                (request.isForMainFrame && request.hasGesture() && request.url.host != Uri.parse(url).host)
                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                            val target = request.url.toString()
                            val path = request.url.path.orEmpty()
                            if (request.url.scheme in listOf("http", "https") &&
                                (path.endsWith(".m3u8", true) || path.endsWith(".mpd", true) || (allowProgressive && path.endsWith(".mp4", true))) && seen.add(target)) {
                                val headers = request.requestHeaders.filterKeys {
                                    it.equals("Referer", true) || it.equals("Origin", true) || it.equals("User-Agent", true) || it.equals("Cookie", true)
                                }.toMutableMap()
                                if (headers.keys.none { it.equals("User-Agent", true) }) headers["User-Agent"] = TvSamaAnimeProvider.USER_AGENT
                                if (headers.keys.none { it.equals("Referer", true) }) headers["Referer"] = url
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    CookieManager.getInstance().getCookie(target)?.let { headers["Cookie"] = it }
                                    if (continuation.isActive) probes += launch {
                                        // Keep the page alive until its actual manifest and media segment
                                        // are checked: some players initialize their session after this request.
                                        val candidate = VideoSource("Direct", target, "UNKNOWN", "Auto", "Volkamax Direct", headers,
                                            mimeType = when {
                                                path.endsWith(".mpd", true) -> "application/dash+xml"
                                                path.endsWith(".mp4", true) -> "video/mp4"
                                                else -> "application/x-mpegURL"
                                            })
                                        val validated = try { validateLiveSource(candidate) }
                                        catch (e: CancellationException) { throw e }
                                        catch (_: Exception) { null }
                                        if (validated != null && continuation.isActive) continuation.resume(validated)
                                    }
                                }
                            }
                            return null
                        }
                    }
                    // Keep the published embed's parent origin and document.referrer.
                    // Opening an iframe URL as a top-level page breaks players that require embedding.
                    val frameUrl = android.text.TextUtils.htmlEncode(url)
                    view.loadDataWithBaseURL(referer,
                        """<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"></head><body style="margin:0"><iframe src="$frameUrl" allow="autoplay; fullscreen" style="border:0;width:100vw;height:100vh"></iframe></body></html>""",
                        "text/html", "UTF-8", null)
                }
            }
        } finally {
            probes.forEach { it.cancel() }
            web?.stopLoading(); web?.onPause(); web?.let { container?.removeView(it) }; web?.destroy()
        }
    } }

internal suspend fun resolveLiveEvent(context: Context, eventUrl: String, excluded: Set<String> = emptySet()): VideoSource = withTimeout(40_000) {
    val provider = com.streamflixreborn.streamflix.providers.VolkaMaxProvider
    var failure: Exception? = null
    val servers = provider.servers(eventUrl).distinctBy { it.src }.filterNot { it.src in excluded }.take(4)
    if (BuildConfig.DEBUG) android.util.Log.d("TvSamaLive", "Lecteurs publiés : ${servers.map { Uri.parse(it.src).host }}")
    // Try native extractors together: one slow host must not delay every other player.
    val native = coroutineScope {
        val results = kotlinx.coroutines.channels.Channel<VideoSource?>(servers.size.coerceAtLeast(1))
        val jobs = servers.map { server -> launch(Dispatchers.IO) {
            val candidate = try {
                withTimeoutOrNull(10_000) {
                    provider.video(server).takeIf { Uri.parse(it.source).scheme in listOf("http", "https") }
                        ?.let { validateLiveSource(VideoSource(server.name, it.source, "UNKNOWN", "Auto", provider.NAME, it.headers.orEmpty(), mimeType = it.type, serverId = server.src)) { reason ->
                            if (BuildConfig.DEBUG) android.util.Log.d("TvSamaLive", "Validation native : $reason")
                        } }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (BuildConfig.DEBUG) android.util.Log.d("TvSamaLive", "Extraction native : ${e.javaClass.simpleName}")
                null
            }
            if (candidate == null && BuildConfig.DEBUG) android.util.Log.d("TvSamaLive", "Lecteur natif non validé : ${Uri.parse(server.src).host}")
            results.trySend(candidate)
        } }
        var found: VideoSource? = null
        for (index in servers.indices) {
            val candidate = results.receive()
            if (candidate != null) { found = candidate; break }
        }
        if (found != null) jobs.forEach { it.cancel() }
        results.close()
        found
    }
    if (native != null) return@withTimeout native
    for (server in servers.take(3)) {
        try {
            val result = withTimeout(if (server == servers.first()) 15_000 else 8_000) { resolveLiveWebPlayer(context, server.src, eventUrl, allowProgressive = false) }
            return@withTimeout result.copy(serverId = server.src)
        } catch (e: TimeoutCancellationException) { currentCoroutineContext().ensureActive(); failure = e }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { failure = e }
    }
    throw IllegalStateException("Aucun lecteur compatible disponible pour ce direct.", failure)
}
