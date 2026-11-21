package fr.nekotv

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebView
import com.streamflixreborn.streamflix.StreamFlixApp
import com.streamflixreborn.streamflix.extractors.VidsrcRuExtractor
import com.streamflixreborn.streamflix.extractors.VidLinkExtractor
import com.streamflixreborn.streamflix.extractors.StreamWishExtractor
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowWebView
import org.robolectric.util.ReflectionHelpers

@Implements(WebView::class)
class ExtractionWebViewShadow : ShadowWebView() {
    @RealObject lateinit var view: WebView
    companion object { var latest: WebView? = null; var cleanupOnMain = true }
    @Implementation override fun loadUrl(url: String) { latest = view; super.loadUrl(url) }
    @Implementation override fun destroy() {
        cleanupOnMain = cleanupOnMain && Looper.myLooper() == Looper.getMainLooper()
        super.destroy()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, shadows = [ExtractionWebViewShadow::class])
@LooperMode(LooperMode.Mode.PAUSED)
class WebExtractionLifecycleTest {
    @Before fun initialize() {
        val app = StreamFlixApp()
        ReflectionHelpers.callInstanceMethod<Void>(app, "attach",
            ReflectionHelpers.ClassParameter.from(Context::class.java, RuntimeEnvironment.getApplication()))
        StreamFlixApp::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, app)
        com.streamflixreborn.streamflix.utils.UserPreferences.setup(app)
        ExtractionWebViewShadow.latest = null
        ExtractionWebViewShadow.cleanupOnMain = true
    }

    @Test fun duplicateManifestCallbacksCompleteOnlyOnce() {
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        val result = scope.async { VidsrcRuExtractor().extract("https://vidsrc.ru/movie/test") }
        try {
            shadowOf(Looper.getMainLooper()).idle()
            val view = ExtractionWebViewShadow.latest!!
            val client = shadowOf(view).webViewClient
            val request = object : WebResourceRequest {
                override fun getUrl() = Uri.parse("https://cdn.example/file2/test.m3u8")
                override fun isForMainFrame() = false
                override fun isRedirect() = false
                override fun hasGesture() = false
                override fun getMethod() = "GET"
                override fun getRequestHeaders() = emptyMap<String, String>()
            }
            Thread { repeat(2) { client.shouldInterceptRequest(view, request) } }.apply { start(); join() }
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(result.isCompleted)
            assertEquals(request.url.toString(), runBlocking { result.await() }.source)
            assertTrue(shadowOf(view).wasDestroyCalled())
        } finally { scope.cancel(); shadowOf(Looper.getMainLooper()).idle() }
    }

    @Test fun cancellingExtractionFromAWorkerCleansUpOnTheMainThread() {
        val context = RuntimeEnvironment.getApplication()
        val extractions: List<Pair<String, suspend () -> Any>> = listOf(
            "VidsrcRu" to { VidsrcRuExtractor().extract("https://player.example/test") },
            "VidLink" to { VidLinkExtractor().extract("https://player.example/test") },
            "StreamWish" to { StreamWishExtractor().resolveRedirectWithWebView(context, "https://player.example/test", "https://wish.example") })
        for ((name, extract) in extractions) {
            val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
            val result = scope.async { extract() }
            shadowOf(Looper.getMainLooper()).idle()
            val view = ExtractionWebViewShadow.latest!!
            Thread { result.cancel() }.apply { start(); join() }
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue("$name: cleanup must run on the UI thread", ExtractionWebViewShadow.cleanupOnMain)
            assertTrue(shadowOf(view).wasDestroyCalled())
            scope.cancel()
        }
    }

    @Test fun javascriptCallbacksAndRendererFailureDoNotCompleteAnExtractionTwice() {
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        val result = scope.async { VidLinkExtractor().extract("https://vidlink.pro/movie/test") }
        try {
            shadowOf(Looper.getMainLooper()).idle()
            val view = ExtractionWebViewShadow.latest!!
            val bridge = shadowOf(view).getJavascriptInterface("Android")
            val callback = bridge.javaClass.getMethod("onStreamFound", String::class.java).apply { isAccessible = true }
            Thread { repeat(2) { callback.invoke(bridge, """{"stream":{"playlist":"https://cdn.example/test.m3u8","captions":[]}}""") } }
                .apply { start(); join() }
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("https://cdn.example/test.m3u8", runBlocking { result.await() }.source)
            assertTrue(shadowOf(view).wasDestroyCalled())
            val stoppedRenderer = object : android.webkit.RenderProcessGoneDetail() {
                override fun didCrash() = true
                override fun rendererPriorityAtExit() = WebView.RENDERER_PRIORITY_BOUND
            }
            assertTrue(shadowOf(view).webViewClient.onRenderProcessGone(view, stoppedRenderer))
            shadowOf(Looper.getMainLooper()).idle()
        } finally { scope.cancel(); shadowOf(Looper.getMainLooper()).idle() }
    }

    @Test fun rendererCrashIsReportedAsExtractionFailureAndReleasesTheWebView() {
        for (extractor in listOf(VidsrcRuExtractor(), VidLinkExtractor())) {
            val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
            val result = scope.async { extractor.extract("https://player.example/test") }
            try {
                shadowOf(Looper.getMainLooper()).idle()
                val view = ExtractionWebViewShadow.latest!!
                val detail = object : android.webkit.RenderProcessGoneDetail() {
                    override fun didCrash() = true
                    override fun rendererPriorityAtExit() = WebView.RENDERER_PRIORITY_BOUND
                }
                assertTrue(shadowOf(view).webViewClient.onRenderProcessGone(view, detail))
                shadowOf(Looper.getMainLooper()).idle()
                assertTrue(result.isCompleted)
                assertTrue(result.isCancelled)
                assertTrue(shadowOf(view).wasDestroyCalled())
            } finally { scope.cancel(); shadowOf(Looper.getMainLooper()).idle() }
        }
    }
}
