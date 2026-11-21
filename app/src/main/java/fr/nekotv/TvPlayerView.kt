package fr.nekotv

import android.content.Context
import android.view.KeyEvent
import android.view.View
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerView

/** OK first reveals the controls and focuses the pause button; a second OK activates it.
 *  Directional keys reveal and focus the corresponding controls. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class TvPlayerView @JvmOverloads constructor(context: Context, attrs: android.util.AttributeSet? = null) : PlayerView(context, attrs) {
    private var consumedConfirmKey: Int? = null
    private var seekKey: Int? = null
    private var seekStartedAt = 0L

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val horizontal = event.keyCode in listOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)
        val focus = findFocus()
        if (horizontal && (seekKey == event.keyCode || !isControllerFullyVisible || focus == null || focus === this || focus is DefaultTimeBar)) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (seekKey != event.keyCode) { seekKey = event.keyCode; seekStartedAt = event.eventTime }
                player?.let { active ->
                    val delta = seekIncrement(event.eventTime - seekStartedAt) * if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1
                    active.seekTo((active.currentPosition + delta).coerceIn(0, active.duration.takeIf { it > 0 } ?: Long.MAX_VALUE))
                }
                showController()
                findViewById<View>(androidx.media3.ui.R.id.exo_progress)?.requestFocus()
            } else if (event.action == KeyEvent.ACTION_UP) seekKey = null
            return true
        }
        if (event.keyCode in listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT) && !isControllerFullyVisible && event.action == KeyEvent.ACTION_DOWN) {
            showController()
            val target = if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP || event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN)
                androidx.media3.ui.R.id.exo_play_pause else androidx.media3.ui.R.id.exo_progress
            findViewById<View>(target)?.requestFocus()
            if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP || event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return true
        }
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
            if (focused === this || focused == null) {
                consumedConfirmKey = event.keyCode
                findViewById<View>(androidx.media3.ui.R.id.exo_play_pause)?.requestFocus()
                return true
            }
            if (focused is DefaultTimeBar) {
                consumedConfirmKey = event.keyCode
                // Commit a pending directional seek, then let the next OK press the pause button.
                focused.onKeyDown(KeyEvent.KEYCODE_DPAD_CENTER, event)
                findViewById<View>(androidx.media3.ui.R.id.exo_play_pause)?.requestFocus()
                return true
            }
        }
        // Native buttons keep their click handler and receive the complete key pair.
        return super.dispatchKeyEvent(event)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) { consumedConfirmKey = null; seekKey = null }
    }
}
