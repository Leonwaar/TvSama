package fr.nekotv

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Checks the pixels of the actual Compose controls, as well as their input actions. */
class ControlFocusDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<PlaybackTestActivity>()

    @Test fun phoneResetRequiresTheRedConfirmationAndCancelKeepsPreferences() {
        val prefs = compose.activity.getSharedPreferences("tvsama_settings", 0)
        prefs.edit().putString("paired_tv_device", "test-tv").commit()
        var historyCleared = false
        compose.runOnUiThread {
            compose.activity.setContent {
                SamaTheme {
                    Box(Modifier.width(320.dp).fillMaxHeight()) {
                        TvSettings("VF", {}, true, {}, false, { historyCleared = true }, {})
                    }
                }
            }
        }
        compose.onNodeWithText("Réinitialiser les préférences").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("test-tv", prefs.getString("paired_tv_device", null)) }
        compose.onNodeWithText("Annuler").performClick()
        compose.runOnIdle { assertEquals("test-tv", prefs.getString("paired_tv_device", null)) }
        compose.onNodeWithText("Réinitialiser les préférences").performClick()
        val confirmation = compose.onNodeWithText("Réinitialiser").assertIsDisplayed()
        val pixels = confirmation.captureToImage().toPixelMap()
        val red = pixels[5, pixels.height / 2]
        assertTrue("Confirmation must be red: $red", red.red > .5f && red.green < .3f && red.blue < .3f)
        confirmation.performClick()
        compose.runOnIdle {
            assertNull(prefs.getString("paired_tv_device", null))
            assertFalse(historyCleared)
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun selectRemoteAndResumeHaveVisibleFocusAndAcceptRemoteTouchAndMouse() {
        var clicks = 0
        compose.runOnUiThread {
            compose.activity.setContent {
                SamaTheme {
                    Column {
                        Action("Sélectionner", Modifier.testTag("select")) { clicks++ }
                        CastRouteButton(Modifier.testTag("remote"))
                        ResumeCard(SavedPlayback(Anime("Reprise test", "Film", emptyList(), "focus-test"),
                            "episode", "Épisode", 1000, 10000), onRemove = {}, onClick = { clicks++ })
                    }
                }
            }
        }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_DOWN)
        compose.waitForIdle()
        val select = compose.onNodeWithTag("select")
        val remote = compose.onNodeWithTag("remote")
        val resume = compose.onNode(hasClickAction() and hasText("Reprise test"))
        for (node in listOf(select, remote, resume)) {
            node.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            node.assertIsFocused()
            val pixels = node.captureToImage().toPixelMap()
            val border = pixels[pixels.width / 2, 1]
            assertTrue("Focused control must have a white border: $border",
                border.red > .95f && border.green > .95f && border.blue > .95f)
        }
        resume.performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(1, clicks) }
        select.performTouchInput { click() }
        compose.runOnIdle { assertEquals(2, clicks) }
        select.performMouseInput { click() }
        compose.runOnIdle { assertEquals(3, clicks) }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_DOWN)
        remote.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        remote.performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Associer la télécommande").assertIsDisplayed()
    }
}
