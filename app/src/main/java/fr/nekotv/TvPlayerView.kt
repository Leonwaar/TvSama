package fr.nekotv

import android.content.Context
import android.view.KeyEvent
import android.view.View
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerView

/** OK reveals hidden controls; on the timeline or video it toggles playback once. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class TvPlayerView(context: Context) : PlayerView(context) {
    private var consumedConfirmKey: Int? = null

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode !in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER)) return super.dispatchKeyEvent(event)
        if (consumedConfirmKey == event.keyCode) {
            if (event.action == KeyEvent.ACTION_UP) consumedConfirmKey = null
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            if (!isControllerFullyVisible) {
                consumedConfirmKey = event.keyCode
                showController()
                findViewById<View>(androidx.media3.ui.R.id.exo_play_pause)?.requestFocus()
                return true
            }
            val focused = findFocus()
            if (focused === this || focused == null || focused is DefaultTimeBar) {
                consumedConfirmKey = event.keyCode
                // Commit a pending directional seek before pausing/resuming.
                if (focused is DefaultTimeBar) focused.onKeyDown(KeyEvent.KEYCODE_DPAD_CENTER, event)
                player?.let { if (it.playWhenReady) it.pause() else it.play() }
                showController()
                return true
            }
        }
        // Native buttons keep their click handler and receive the complete key pair.
        return super.dispatchKeyEvent(event)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) consumedConfirmKey = null
    }
}
