package fr.nekotv

import android.app.Application
import android.graphics.BitmapFactory
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class NavigationSectionsTest {
    @Test fun completedContentIsNotATopLevelPage() {
        assertFalse(Page.entries.any { it.label == "Contenu visionné" })
    }

    @Test fun headerLogoHasNoOpaqueBackground() {
        val context = RuntimeEnvironment.getApplication()
        val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.tvsama_witch)
        assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
        assertEquals(0, Color.alpha(bitmap.getPixel(bitmap.width / 2, 0)))
    }
}
