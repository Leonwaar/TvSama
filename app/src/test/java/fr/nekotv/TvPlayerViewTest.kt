package fr.nekotv

import android.app.Application
import android.view.KeyEvent
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class TvPlayerViewTest {
    @Test fun hiddenControlsRevealOnFirstOkWithoutPausingAndIgnoreKeyRepeat() {
        val context = RuntimeEnvironment.getApplication()
        val player = ExoPlayer.Builder(context).build()
        try {
            val view = TvPlayerView(context).apply { this.player = player }
            player.play(); view.hideController()
            view.dispatchKeyEvent(KeyEvent(1000, 1000, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0))
            assertTrue("The first OK must only reveal the controls", player.playWhenReady)
            view.dispatchKeyEvent(KeyEvent(1000, 1500, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 3))
            view.dispatchKeyEvent(KeyEvent(1000, 1600, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0))
            assertTrue("Holding OK must not pause either", player.playWhenReady)
        } finally { player.release() }
    }
    @Test fun horizontalTapSeeksFifteenSecondsAndHoldAcceleratesWithoutPausing() {
        val context = RuntimeEnvironment.getApplication()
        val player = ExoPlayer.Builder(context).build()
        try {
            player.setMediaItem(MediaItem.fromUri("https://media.example/video.mp4"))
            val view = TvPlayerView(context).apply { this.player = player }
            player.play(); view.hideController()
            view.dispatchKeyEvent(KeyEvent(1000, 1000, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 0))
            assertEquals(15_000, player.currentPosition)
            view.dispatchKeyEvent(KeyEvent(1000, 9000, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 8))
            assertEquals(135_000, player.currentPosition)
            view.dispatchKeyEvent(KeyEvent(1000, 9100, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT, 0))
            assertTrue(player.playWhenReady)
            view.hideController()
            view.dispatchKeyEvent(KeyEvent(10_000, 10_000, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, 0))
            assertEquals(120_000, player.currentPosition)
        } finally { player.release() }
    }
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test fun segmentsColorTheTrackWithoutCoveringTheThumb() {
        val context = RuntimeEnvironment.getApplication()
        val view = android.view.LayoutInflater.from(context).inflate(R.layout.tvsama_player_view, null) as TvPlayerView
        val bar = view.findViewById<SegmentTimeBar>(androidx.media3.ui.R.id.exo_progress)
        bar.layout(0, 0, 1000, 40)
        bar.setDuration(100_000)
        bar.setPosition(50_000)
        bar.setScrubberColor(android.graphics.Color.WHITE)
        bar.setSegments(listOf(
            SkipSegment("Intro", IntroSegment(10_000, 60_000)),
            SkipSegment("Outro", IntroSegment(80_000, 100_000), ending = true)
        ))
        val bitmap = android.graphics.Bitmap.createBitmap(1000, 40, android.graphics.Bitmap.Config.ARGB_8888)
        bar.draw(android.graphics.Canvas(bitmap))
        assertEquals(0xFF71E4E8.toInt(), bitmap.getPixel(300, 20))
        assertEquals(0xFFFFD166.toInt(), bitmap.getPixel(900, 20))
        assertEquals(android.graphics.Color.WHITE, bitmap.getPixel(500, 20))
        assertEquals(android.graphics.Color.TRANSPARENT, bitmap.getPixel(300, 14))
    }

    @Test fun playerLayoutUsesSegmentBarAndClampsMarkers() {
        val context = RuntimeEnvironment.getApplication()
        val view = android.view.LayoutInflater.from(context).inflate(R.layout.tvsama_player_view, null) as TvPlayerView
        val bar = view.findViewById<SegmentTimeBar>(androidx.media3.ui.R.id.exo_progress)
        assertNotNull(bar)
        bar.layout(0, 0, 1000, 40)
        bar.setDuration(100_000)
        assertNull(bar.markerBounds(IntroSegment(-1, 10_000)))
        val bounds = bar.markerBounds(IntroSegment(50_000, 120_000))!!
        assertEquals(500f, bounds.left, 1f)
        assertTrue(bounds.right <= 1000f)
        assertEquals(20f, bounds.centerY(), 0.01f)
        assertEquals(4f * context.resources.displayMetrics.density, bounds.height(), 0.01f)
    }
}
