package fr.nekotv

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.webkit.*
import com.streamflixreborn.streamflix.providers.TvSamaAnimeProvider
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private data class WebCapture(val address: String, val headers: MutableMap<String, String>,
    val mime: String, val at: Long)

/** Runs the source's player to capture the actual manifest, including request headers.
 *  When [captureGate] is set, media requests are queued until that JavaScript expression
 *  returns "ready". A "failed" result aborts instead of waiting for the full timeout. */
@SuppressLint("SetJavaScriptEnabled")
internal suspend fun resolveLiveWebPlayer(context: Context, url: String, referer: String, allowProgressive: Boolean = true,
    directPage: Boolean = false, iframeBootstrap: String = "", captureGate: String = "",
    captureFailureValue: String = "failed", captureSubtitles: Boolean = false): VideoSource =
    withContext(Dispatchers.Main) { coroutineScope {
        var web: WebView? = null
        var container: android.view.ViewGroup? = null
        val probes = mutableListOf<Job>()
        try {
            withTimeout(25_000) {
                suspendCancellableCoroutine { continuation ->
                    val pendingCaptures = java.util.Collections.synchronizedList(mutableListOf<WebCapture>())
                    val capturedSubtitles = java.util.Collections.synchronizedList(mutableListOf<String>())
                    var gateWatcher: Job? = null
                    val view = object : WebView(context) {
                        override fun dispatchTouchEvent(event: android.view.MotionEvent) = false
                    }
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
                    suspend fun gateState(): String = suspendCancellableCoroutine { probe ->
                        try {
                            view.evaluateJavascript("(function(){try{return String($captureGate||'');}catch(e){return '';}})()") { value ->
                                probe.resume(value?.trim('"').orEmpty())
                            }
                        } catch (_: Exception) { probe.resume("") }
                    }
                    suspend fun awaitGate(): String {
                        val deadline = System.currentTimeMillis() + 12_000
                        while (continuation.isActive && System.currentTimeMillis() < deadline) {
                            val value = gateState()
                            if (value == "ready" || value == captureFailureValue) return value
                            delay(250)
                        }
                        return ""
                    }
                    suspend fun validate(capture: WebCapture): VideoSource? {
                        CookieManager.getInstance().getCookie(capture.address)?.let { capture.headers["Cookie"] = it }
                        val sidecars = synchronized(capturedSubtitles) { capturedSubtitles.toList() }.distinct()
                            .map { address -> VideoSubtitle(address, "", address.substringAfterLast('/').substringBefore('?')) }
                        val candidate = VideoSource("Direct", capture.address, "UNKNOWN", "Auto", "Direct",
                            capture.headers, subtitles = sidecars, mimeType = capture.mime)
                        return try { withTimeoutOrNull(8_000) { validateLiveSource(candidate) } }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { null }
                    }
                    fun watchGate() {
                        if (gateWatcher?.isActive == true) return
                        gateWatcher = launch {
                            val status = awaitGate()
                            if (!continuation.isActive) return@launch
                            if (status == captureFailureValue) {
                                continuation.resumeWithException(
                                    IllegalStateException("Aucune piste confirmée pour cette version."))
                                return@launch
                            }
                            if (status == "ready") {
                                // Let the final selection issue its own manifest request.
                                delay(350)
                                val snapshot = synchronized(pendingCaptures) { pendingCaptures.toList().asReversed() }
                                for (capture in snapshot) {
                                    if (!continuation.isActive) break
                                    val validated = validate(capture) ?: continue
                                    if (continuation.isActive) continuation.resume(validated)
                                    break
                                }
                            }
                            gateWatcher = null
                        }
                    }
                    // Watch the gate from the start: a player that rejects every track fails
                    // long before any manifest is requested, and the retry must not wait out
                    // the whole budget.
                    if (captureGate.isNotBlank()) watchGate()
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
                            !com.streamflixreborn.streamflix.utils.SourceWebSession.allowsNavigation(request.url.toString()) ||
                                (request.isForMainFrame && request.hasGesture() && request.url.host != Uri.parse(url).host)
                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                            val target = request.url.toString()
                            val path = request.url.path.orEmpty()
                            if (captureSubtitles && request.url.scheme in listOf("http", "https") &&
                                (path.endsWith(".vtt", true) || path.endsWith(".srt", true))) {
                                synchronized(capturedSubtitles) {
                                    if (target !in capturedSubtitles) capturedSubtitles.add(target)
                                    while (capturedSubtitles.size > 20) capturedSubtitles.removeAt(0)
                                }
                            }
                            if (request.url.scheme in listOf("http", "https") &&
                                (path.endsWith(".m3u8", true) || path.endsWith(".mpd", true) || (allowProgressive && path.endsWith(".mp4", true))) && seen.add(target)) {
                                val headers = request.requestHeaders.filterKeys {
                                    it.equals("Referer", true) || it.equals("Origin", true) || it.equals("User-Agent", true) || it.equals("Cookie", true)
                                }.toMutableMap()
                                if (headers.keys.none { it.equals("User-Agent", true) }) headers["User-Agent"] = TvSamaAnimeProvider.USER_AGENT
                                if (headers.keys.none { it.equals("Referer", true) }) headers["Referer"] = url
                                val mime = when {
                                    path.endsWith(".mpd", true) -> "application/dash+xml"
                                    path.endsWith(".mp4", true) -> "video/mp4"
                                    else -> "application/x-mpegURL"
                                }
                                if (captureGate.isNotBlank()) {
                                    pendingCaptures.add(WebCapture(target, headers, mime, System.currentTimeMillis()))
                                    while (pendingCaptures.size > 10) pendingCaptures.removeAt(0)
                                    watchGate()
                                } else android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    CookieManager.getInstance().getCookie(target)?.let { headers["Cookie"] = it }
                                    if (continuation.isActive) probes += launch {
                                        // Keep the page alive until its actual manifest and media segment
                                        // are checked: some players initialize their session after this request.
                                        val candidate = VideoSource("Direct", target, "UNKNOWN", "Auto", "Direct", headers, mimeType = mime)
                                        val validated = try { validateLiveSource(candidate) }
                                        catch (e: CancellationException) { throw e }
                                        catch (_: Exception) { null }
                                        if (validated != null && continuation.isActive) continuation.resume(validated)
                                        else seen.remove(target)
                                    }
                                }
                            }
                            return null
                        }
                    }
                    if (directPage) {
                        // Some players only expose their video element to a top-level page,
                        // which lets the bootstrap script start playback and unmute it.
                        view.loadUrl(url)
                    } else {
                        // Keep the published embed's parent origin and document.referrer.
                        // Opening an iframe URL as a top-level page breaks players that require embedding.
                        val frameUrl = android.text.TextUtils.htmlEncode(url)
                        val bootstrap = iframeBootstrap.takeIf { it.isNotBlank() }
                            ?.let { "<script>$it</script>" }.orEmpty()
                        view.loadDataWithBaseURL(referer,
                            """<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"></head><body style="margin:0"><iframe id="player" src="$frameUrl" allow="autoplay; fullscreen; encrypted-media; picture-in-picture" allowfullscreen style="border:0;width:100vw;height:100vh"></iframe>$bootstrap</body></html>""",
                            "text/html", "UTF-8", null)
                    }
                }
            }
        } finally {
            probes.forEach { it.cancel() }
            web?.stopLoading(); web?.onPause(); web?.let { container?.removeView(it) }; web?.destroy()
        }
    } }

/** Every reader gets its own budget; one stalled reader cannot consume the other's attempt. */
internal suspend fun <T : Any, R : Any> firstLiveReader(readers: List<T>, timeoutMillis: Long, resolve: suspend (T) -> R?): R? = coroutineScope {
    val results = kotlinx.coroutines.channels.Channel<R?>(readers.size.coerceAtLeast(1))
    val jobs = readers.map { reader -> launch {
        val candidate = try { withTimeoutOrNull(timeoutMillis) { resolve(reader) } }
        catch (e: TimeoutCancellationException) { currentCoroutineContext().ensureActive(); null }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { null }
        results.trySend(candidate)
    } }
    try {
        for (index in readers.indices) results.receive()?.let { return@coroutineScope it }
        null
    } finally { jobs.forEach { it.cancel() }; results.close() }
}

internal suspend fun <T : Any, R : Any> firstLiveReaderByLanguage(readers: List<T>, timeoutMillis: Long, priority: (T) -> Int, resolve: suspend (T) -> R?): R? {
    val french = readers.filter { priority(it) == 0 }
    return firstLiveReader(french, timeoutMillis, resolve)
        ?: firstLiveReader(readers.filter { priority(it) != 0 }, timeoutMillis, resolve)
}

internal suspend fun resolveLiveEvent(context: Context, eventUrl: String, excluded: Set<String> = emptySet(), preferredServerId: String? = null): VideoSource = withTimeout(40_000) {
    if (FrenchLiveTv.owns(eventUrl)) {
        val readers = FrenchLiveTv.servers(eventUrl).filterNot { it.src in excluded }
            .filter { preferredServerId == null || it.src == preferredServerId }.take(4)
        return@withTimeout firstLiveReader(readers, 8_000) { server ->
            validateLiveSource(VideoSource(server.name, server.src, "VF", "Auto", "TVradioZap", serverId = server.src))
        } ?: error("Aucun flux français utilisable pour cette chaîne. Essayez une autre chaîne.")
    }
    val streamed = StreamedLive.owns(eventUrl)
    check(streamed) { "Cette ancienne source de direct a été retirée. Choisissez un direct dans le catalogue." }
    val servers = StreamedLive.servers(eventUrl).distinctBy { it.src }.sortedBy { liveLanguagePriority(it.name) }
        .filterNot { it.src in excluded }.filter { preferredServerId == null || it.src == preferredServerId }.take(4)
    check(servers.isNotEmpty()) { "Aucun lecteur publié disponible pour ce direct." }
    val native = firstLiveReaderByLanguage(servers, 5_000, { liveLanguagePriority(it.name) }) { server -> withContext(Dispatchers.IO) {
        com.streamflixreborn.streamflix.extractors.Extractor.extract(server.src, server).takeIf { Uri.parse(it.source).scheme in listOf("http", "https") }?.let { video ->
            validateLiveSource(VideoSource(server.name, video.source, if (liveLanguagePriority(server.name) == 0) "VF" else "UNKNOWN", "Auto", "Streamed", video.headers.orEmpty(), mimeType = video.type, serverId = server.src))
        }
    } }
    if (native != null) return@withTimeout native
    if (streamed) {
        // One WebView at a time keeps the projector's memory bounded.
        for (server in servers.take(2)) {
            val source = withTimeoutOrNull(12_000) {
                try { resolveLiveWebPlayer(context, server.src, eventUrl, allowProgressive = false)
                    .copy(provider = "Streamed", serverId = server.src, name = server.name, language = if (liveLanguagePriority(server.name) == 0) "VF" else "UNKNOWN") }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { null }
            }
            if (source != null) return@withTimeout source
        }
    }
    // Protected players need their real page, a visible session and sometimes a user gesture.
    throw IllegalStateException("Ce lecteur doit terminer son chargement dans la page du site.")
}
