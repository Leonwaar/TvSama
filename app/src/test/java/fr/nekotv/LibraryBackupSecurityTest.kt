package fr.nekotv

import android.app.Application
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class LibraryBackupSecurityTest {
    @Test fun exportAndRestoreKeepCredentialsOnTheirOriginalDevice() {
        val context = RuntimeEnvironment.getApplication()
        val settings = context.getSharedPreferences("tvsama_settings", 0)
        settings.edit().clear().putString("pairing_token", "local-secret")
            .putString("paired_tv_token", "tv-secret").putString("paired_tv_host", "192.168.1.2")
            .putBoolean("subtitles", false).commit()
        val files = exportLibraryPreferences(context)
        assertFalse(files.toString().contains("local-secret"))
        assertFalse(files.toString().contains("tv-secret"))
        assertFalse(files.toString().contains("192.168.1.2"))
        val incoming = files.getJSONObject("tvsama_settings")
        incoming.put("pairing_token", JSONObject().put("type", "string").put("value", "foreign-secret"))
        incoming.put("paired_tv_host", JSONObject().put("type", "string").put("value", "192.168.1.99"))
        settings.edit().putBoolean("subtitles", true).commit()
        restoreLibraryPreferences(context, files)
        assertEquals("local-secret", settings.getString("pairing_token", null))
        assertEquals("tv-secret", settings.getString("paired_tv_token", null))
        assertEquals("192.168.1.2", settings.getString("paired_tv_host", null))
        assertFalse(settings.getBoolean("subtitles", true))
    }
}
