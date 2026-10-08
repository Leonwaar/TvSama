package fr.nekotv

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
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

/** Login and interactive checks are completed with the source, without storing passwords. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SourceAccess(name: String, url: String, onClose: () -> Unit, onRetry: () -> Unit) {
    val context = LocalContext.current
    val web = remember(url) { WebView(context).apply {
        settings.javaScriptEnabled = true; settings.domStorageEnabled = true
        settings.userAgentString = com.streamflixreborn.streamflix.providers.TvSamaAnimeProvider.USER_AGENT
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean =
                request.url.scheme !in listOf("https", "http")
        }
        loadUrl(url)
    } }
    DisposableEffect(web) { onDispose { CookieManager.getInstance().flush(); web.stopLoading(); web.destroy() } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        androidx.compose.material3.Surface(Modifier.fillMaxSize().padding(16.dp), color = Ink) {
            Column(Modifier.padding(12.dp)) {
                Text("Accès à $name")
                Text("Validez l’accès demandé par le site. La session sera conservée sur cet appareil.", color = Muted)
                AndroidView(factory = { web }, modifier = Modifier.weight(1f).fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Action("Fermer", onClick = onClose)
                    Action("Réessayer dans TvSama") { CookieManager.getInstance().flush(); onClose(); onRetry() }
                }
            }
        }
    }
}
