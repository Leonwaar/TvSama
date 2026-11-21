package fr.nekotv

import android.app.Application
import android.content.Context
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.streamflixreborn.streamflix.StreamFlixApp
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, shadows = [ExtractionWebViewShadow::class])
@LooperMode(LooperMode.Mode.PAUSED)
class SourceAccessRecoveryTest {
    @get:Rule val compose = createAndroidComposeRule<PlaybackTestActivity>()
    @Before fun initialize() {
        val app = StreamFlixApp()
        ReflectionHelpers.callInstanceMethod<Void>(app, "attach",
            ReflectionHelpers.ClassParameter.from(Context::class.java, RuntimeEnvironment.getApplication()))
        StreamFlixApp::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, app)
        com.streamflixreborn.streamflix.utils.UserPreferences.setup(app)
        ExtractionWebViewShadow.latest = null
    }
    @Test fun rendererFailureClosesTheDeadViewAndRetryCreatesAFreshBrowser() {
        compose.runOnUiThread { compose.activity.setContent {
            SamaTheme { SourceAccess("Source", "https://source.example/", {}, {}) }
        } }
        compose.waitForIdle()
        val previous = ExtractionWebViewShadow.latest!!
        compose.runOnUiThread {
            assertTrue(shadowOf(previous).webViewClient.onRenderProcessGone(previous, object : RenderProcessGoneDetail() {
                override fun didCrash() = true
                override fun rendererPriorityAtExit() = WebView.RENDERER_PRIORITY_BOUND
            }))
        }
        compose.waitForIdle()
        assertTrue(shadowOf(previous).wasDestroyCalled())
        compose.onNodeWithText("Réessayer").performClick()
        compose.waitForIdle()
        assertNotSame(previous, ExtractionWebViewShadow.latest)
        assertFalse(shadowOf(ExtractionWebViewShadow.latest!!).wasDestroyCalled())
    }
}
