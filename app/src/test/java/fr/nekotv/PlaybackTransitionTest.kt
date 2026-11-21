package fr.nekotv

import android.app.Application
import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.streamflixreborn.streamflix.StreamFlixApp
import com.streamflixreborn.streamflix.utils.UserPreferences
import org.junit.Before
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class PlaybackTransitionTest {
    @get:Rule val compose = createAndroidComposeRule<PlaybackTestActivity>()

    @Before fun initialize() {
        val app = StreamFlixApp()
        ReflectionHelpers.callInstanceMethod<Void>(app, "attach",
            ReflectionHelpers.ClassParameter.from(Context::class.java, RuntimeEnvironment.getApplication()))
        StreamFlixApp::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, app)
        UserPreferences.setup(app)
    }

    @Test fun fullscreenUsesBlackSystemBarsAndAllowsTheCameraCutout() {
        compose.runOnUiThread {
            setPlayerFullscreen(compose.activity, true)
            assertEquals(android.graphics.Color.BLACK, compose.activity.window.statusBarColor)
            assertEquals(android.graphics.Color.BLACK, compose.activity.window.navigationBarColor)
            assertEquals(android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES,
                compose.activity.window.attributes.layoutInDisplayCutoutMode)
            setPlayerFullscreen(compose.activity, false)
            assertEquals(android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT,
                compose.activity.window.attributes.layoutInDisplayCutoutMode)
        }
    }

    @Test fun replacingLoadingContentDoesNotLeaveAConflictingMediaSession() {
        val loading = mutableStateOf(false)
        compose.runOnUiThread {
            compose.activity.setContent {
                if (loading.value) LoadingDialog("Chargement") { loading.value = false }
                compose.activity.source.value?.let { video ->
                    TvSamaPlayer(source = video, title = "Test", onProgress = { _, _ -> })
                }
            }
        }
        var previous: ExoPlayer? = null
        repeat(3) { index ->
            compose.runOnUiThread { loading.value = true }
            compose.waitForIdle()
            compose.runOnUiThread {
                compose.activity.source.value = VideoSource("Test", "http://127.0.0.1:1/$index.mp4", "VF", "Auto", "Test")
                loading.value = false
            }
            compose.waitForIdle()
            compose.runOnUiThread {
                val current = findPlayer(compose.activity.window.decorView)
                assertNotNull("The player must be initialized", current)
                assertNotSame(previous, current)
                assertEquals("http://127.0.0.1:1/$index.mp4", current!!.currentMediaItem!!.localConfiguration!!.uri.toString())
                previous = current
            }
        }
        compose.runOnUiThread { compose.activity.source.value = null }
        compose.waitForIdle()
    }

    private fun findPlayer(view: View): ExoPlayer? {
        if (view is PlayerView) return view.player as? ExoPlayer
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findPlayer(view.getChildAt(index))?.let { return it }
        }
        return null
    }
}
