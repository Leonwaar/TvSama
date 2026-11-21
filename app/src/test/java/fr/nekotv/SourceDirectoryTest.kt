package fr.nekotv

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class SourceDirectoryTest {
    @Test fun wikiCandidatesComeOnlyFromTheNamedFrenchStreamingEntries() {
        val document = Jsoup.parse("""<h2>English</h2><h3>Streaming</h3><ul><li><a href="https://wrong.example/">flemmix</a></li></ul>
            <h2>French / Français</h2><h3>Downloading</h3><ul><li><a href="https://wrong.example/">flemmix</a></li></ul>
            <h3>Streaming</h3><ul><li><a href="https://flemmix.next/">flemmix</a><a href="https://ads.example/">Status</a></li>
            <li><a href="https://anime.next/">anime-sama</a></li><li><a href="https://vostfree.next/">vostfree</a></li></ul><h2>German</h2><h3>Streaming</h3>
            <ul><li><a href="https://wrong.example/">flemmix</a></li></ul>""")
        val records = SourceDirectory.parseWiki(document)
        assertEquals(setOf("Wiflix", "Anime-Sama", "Vostfree"), records.map { it.name }.toSet())
        assertTrue(records.all { it.status == "candidate" && it.origin == "Wiki FMHY" })
        assertFalse(records.any { it.url.contains("wrong") || it.url.contains("ads") })
    }
    @Test fun primaryKeepsOfflineReportsWithoutAnAddressButRejectsUnsafeActiveRecords() {
        val records = SourceDirectory.parsePrimaryPage(org.json.JSONObject("""{"data":[
            {"name":"Site arrêté","slug":"offline-site","language":"fr","category":"films","status":"offline","current_url":null},
            {"name":"Actif","slug":"active-site","language":"multi","category":"anime","status":"active","current_url":{"url":"https://catalogue.example/"}},
            {"name":"Local","slug":"local","language":"fr","category":"films","status":"active","current_url":{"url":"https://127.0.0.1/"}}
            ]}"""))
        assertEquals(2, records.size)
        assertEquals("offline", records.first().status)
        assertEquals("", records.first().url)
        assertEquals("OùStreamer", records.last().origin)
    }
    @Test fun backupUsesOnlyActivePublishedAddressCardsAndRecordsItsOrigin() {
        val document = Jsoup.parse("""<section><h2>URLs:</h2>
            <a href="https://coflix.next/"><code>https://coflix.next/</code><span>Actif</span></a>
            <a href="https://coflix.old/"><code>https://coflix.old/</code><span>Inactif</span></a>
            <a href="https://ad.example/"><code>https://coflix.next/</code><span>Actif</span></a>
            <a href="https://127.0.0.1/"><code>https://127.0.0.1/</code><span>Actif</span></a>
            <a href="https://t.me/example"><code>https://t.me/example</code><span>Actif</span></a>
            </section><section><a href="https://ad.example/"><code>https://ad.example/</code><span>Actif</span></a></section>""", "https://adressesdirect.com/websites/coflix")
        val records = SourceDirectory.parseFallback("Coflix", "coflix", document)
        assertEquals(listOf("https://coflix.next/"), records.map { it.url })
        assertEquals("AdressesDirect", records.single().origin)
        assertEquals("https://adressesdirect.com/websites/coflix", records.single().permanentUrl)
    }
    @Test fun directoryMessagesDoNotTurnABlockOrAddressChangeIntoAnOutage() {
        assertTrue(reportedDirectoryStatus("offline").contains("Hors ligne selon"))
        assertTrue(reportedDirectoryStatus("blocked").contains("pas une panne confirmée"))
        assertTrue(reportedDirectoryStatus("redirected").contains("adresse"))
        assertFalse(reportedDirectoryStatus("unknown").contains("Hors ligne"))
        listOf("http://site.example/", "https://user:secret@site.example/", "https://192.168.1.1/", "https://[::1]/", "https://device.local/", "javascript:alert(1)")
            .forEach { assertNull(it, safeDirectoryAddress(it)) }
    }
}
