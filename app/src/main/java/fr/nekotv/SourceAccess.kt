package fr.nekotv

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

/** Login and interactive checks are completed with the source, without storing passwords. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SourceAccess(name: String, url: String, onClose: () -> Unit, onRetry: () -> Unit, playback: Boolean = false,
    onPlay: ((VideoSource) -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var captured by remember(url) { mutableStateOf<VideoSource?>(null) }
    var checking by remember(url) { mutableStateOf(false) }
    var progress by remember(url) { mutableIntStateOf(0) }
    var error by remember(url) { mutableStateOf("") }
    var retrying by remember(url) { mutableStateOf(false) }
    var webRevision by remember(url) { mutableIntStateOf(0) }
    var rendererFailed by remember(url) { mutableStateOf(false) }
    val deadViews = remember { mutableSetOf<WebView>() }
    var fullscreen by remember(url) { mutableStateOf<android.view.View?>(null) }
    var fullscreenCallback by remember(url) { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    fun closeFullscreen() { fullscreen = null; val callback = fullscreenCallback; fullscreenCallback = null; callback?.onCustomViewHidden() }
    val web = remember(url, webRevision) { WebView(context).apply {
        settings.javaScriptEnabled = true; settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.useWideViewPort = true; settings.loadWithOverviewMode = true
        settings.userAgentString = com.streamflixreborn.streamflix.providers.TvSamaAnimeProvider.USER_AGENT
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        webViewClient = object : WebViewClient() {
            override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                deadViews.add(view)
                rendererFailed = true; captured = null; checking = false; retrying = false; progress = 100
                closeFullscreen()
                error = "La page s’est interrompue. Appuyez sur Réessayer pour la rouvrir."
                (view.parent as? android.view.ViewGroup)?.removeView(view)
                view.destroy()
                return true
            }
            override fun shouldInterceptRequest(view: WebView, request: android.webkit.WebResourceRequest): android.webkit.WebResourceResponse? {
                if (onPlay != null) browserMediaCandidate(request.url.toString(), request.requestHeaders, url, name)?.let { candidate ->
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        val cookies = CookieManager.getInstance().getCookie(candidate.url)
                        captured = if (cookies.isNullOrBlank()) candidate else candidate.copy(headers =
                            candidate.headers.filterKeys { !it.equals("Cookie", true) } + ("Cookie" to cookies))
                    }
                }
                return null
            }
            override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean =
                !com.streamflixreborn.streamflix.utils.SourceWebSession.allowsNavigation(request.url.toString())
            override fun onPageStarted(view: WebView, page: String, favicon: android.graphics.Bitmap?) { error = ""; progress = 0; captured = null }
            override fun onPageFinished(view: WebView, page: String) {
                CookieManager.getInstance().flush()
                if (playback) view.evaluateJavascript("""(function(){var f=document.querySelector('iframe:not([src*=chatango])');if(f){f.scrollIntoView();window.scrollBy(0,-60);}})();""", null)
            }
            override fun onReceivedHttpError(view: WebView, request: android.webkit.WebResourceRequest, response: android.webkit.WebResourceResponse) {
                if (request.isForMainFrame) error = if (response.statusCode in listOf(401, 403, 429))
                    "Le site demande une validation. Terminez-la dans cette page." else "Le site répond HTTP ${response.statusCode}."
            }
            override fun onReceivedError(view: WebView, request: android.webkit.WebResourceRequest, failure: android.webkit.WebResourceError) {
                if (request.isForMainFrame) error = "Cette page ne répond pas. Réessayez ou choisissez une autre source."
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, value: Int) { progress = value }
            override fun onShowCustomView(view: android.view.View, callback: CustomViewCallback) {
                fullscreen = view; fullscreenCallback = callback
            }
            override fun onHideCustomView() { closeFullscreen() }
        }
        loadUrl(url)
    } }
    DisposableEffect(web) { onDispose {
        closeFullscreen(); CookieManager.getInstance().flush()
        if (!deadViews.remove(web)) { web.stopLoading(); web.destroy() }
    } }
    Dialog(onDismissRequest = { if (fullscreen != null) closeFullscreen() else if (!rendererFailed && web.canGoBack()) web.goBack() else onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        androidx.compose.material3.Surface(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp), color = Ink) {
            if (fullscreen != null) Box {
                AndroidView(factory = { fullscreen!! }, modifier = Modifier.fillMaxSize())
                Action("Fermer le plein écran", modifier = Modifier.align(androidx.compose.ui.Alignment.TopEnd)) { closeFullscreen() }
            } else {
            Column(Modifier.padding(12.dp)) {
                Text(if (playback) "Lecture · $name" else "Accès à $name")
                Text(if (playback) "Laissez le lecteur charger et validez la demande du site si nécessaire. La vidéo lue reste transférée au lecteur TvSama." else "Validez le captcha et ouvrez un lecteur dans cette page, puis choisissez « J’ai validé · réessayer ». La session sera conservée sur cet appareil.", color = Muted)
                if (progress < 100) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
                if (error.isNotBlank()) Text(error, color = Coral)
                if (!rendererFailed) key(web) { AndroidView(factory = { web }, modifier = Modifier.weight(1f).fillMaxWidth()) }
                else Spacer(Modifier.weight(1f))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Action("Fermer", onClick = onClose)
                    if (onPlay != null && captured != null) Action(if (checking) "Vérification…" else "Lire la vidéo affichée dans TvSama", enabled = !checking) {
                        val candidate = captured ?: return@Action
                        checking = true
                        scope.launch {
                            try {
                                var failure = ""
                                val verified = validateLiveSource(candidate) { failure = it }
                                if (verified != null) { onClose(); onPlay(verified) }
                                else error = failure.ifBlank { "Cette vidéo ne peut pas être transférée. Essayez un autre lecteur sur la page." }
                            } finally { checking = false }
                        }
                    }
                    Action(if (error.isNotBlank()) "Réessayer" else "↻ Page") {
                        error = ""
                        if (rendererFailed) { rendererFailed = false; webRevision++ } else web.reload()
                    }
                    if (!playback) Action("J’ai validé · réessayer", enabled = !retrying && !rendererFailed) {
                        retrying = true
                        web.evaluateJavascript("""(function(){
                            var pages=[];
                            function read(w,depth){try{
                                var html=w.document.documentElement.outerHTML;
                                if(typeof w._fx_k==='string')html+='<script>window._fx_k='+JSON.stringify(w._fx_k)+';</script>';
                                pages.push({url:w.location.href,html:html});
                                if(depth<1)for(var i=0;i<w.frames.length&&pages.length<4;i++)read(w.frames[i],depth+1);
                            }catch(e){}}
                            read(window,0);return pages;
                        })()""") { json ->
                            runCatching { com.google.gson.JsonParser.parseString(json).asJsonArray }.getOrNull()?.forEach { page ->
                                runCatching { page.asJsonObject }.getOrNull()?.let { entry ->
                                    com.streamflixreborn.streamflix.utils.SourceWebSession.rememberValidatedPage(
                                        entry.get("url")?.asString.orEmpty(), entry.get("html")?.asString.orEmpty())
                                }
                            }
                            CookieManager.getInstance().flush(); onClose(); onRetry()
                        }
                    }
                }
            }
            }
        }
    }
}
