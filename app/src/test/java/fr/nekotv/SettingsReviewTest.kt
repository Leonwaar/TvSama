package fr.nekotv

import org.junit.Assert.*
import org.junit.Test

class SettingsReviewTest {
    @Test fun startupUsesOnlyTheThreeSupportedDestinations() {
        assertEquals(Page.HISTORY, startupPage("Reprendre"))
        assertEquals(Page.FAVORITES, startupPage("Ma liste"))
        assertEquals(Page.HOME, startupPage(null))
        assertEquals(Page.HOME, startupPage("Lecture"))
    }
    @Test fun pairingSecretsAndDeviceIdentityNeverTravelInLibraryBackups() {
        localPairingKeys.forEach { assertFalse(portablePreference("tvsama_settings", it)) }
        assertTrue(portablePreference("tvsama_settings", "subtitles"))
        assertTrue(portablePreference("tvsama_library", "history"))
    }
}
