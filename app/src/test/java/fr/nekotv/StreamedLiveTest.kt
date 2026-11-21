package fr.nekotv

import org.json.JSONArray
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [28])
class StreamedLiveTest {
    @Test fun mirrorsComeOnlyFromStreamedSportsEntry() {
        val doc = Jsoup.parse("""<h3 id="live-sports">Live Sports</h3><ul>
            <li><a href="https://streamed.pk/">Streamed</a><a href="https://streamed.st/">2</a><a href="https://strmd.link/">Mirrors</a></li>
            <li><a href="https://other.example/">Other</a><a href="https://other2.example/">2</a></li></ul>
            <h3>Other section</h3><ul><li><a href="https://unrelated.example/">Streamed</a></li></ul>""")
        assertEquals(listOf("https://streamed.pk/", "https://streamed.st/"), SourceDirectory.parseLiveWiki(doc).map { it.url })
    }
    @Test fun liveFlagComesFromPublishedLiveListNotStartTime() {
        val input = JSONArray("""[{"id":"match","title":"A vs B","date":1000,"category":"football","sources":[{"source":"alpha","id":"a"}]},
            {"id":"empty","title":"No player","date":1000,"sources":[]}]""")
        val events = StreamedLive.parseEvents(input, "https://streamed.st/", emptySet())
        assertEquals(1, events.size)
        assertFalse(events.single().isLive)
        assertEquals("https://streamed.st/watch/match", events.single().url)
        assertTrue(StreamedLive.parseEvents(input, "https://streamed.st/", setOf("match")).single().isLive)
    }
    @Test fun frenchReadersArePrioritizedWithoutInventingTheirLanguage() {
        assertEquals(0, liveLanguagePriority("Lecteur 2 · FR · HD"))
        assertEquals(0, liveLanguagePriority("beIN Sports Français"))
        assertEquals(1, liveLanguagePriority("Main"))
        assertEquals(2, liveLanguagePriority("English - Sky Sports"))
        assertEquals(1, liveLanguagePriority("Refresh"))
    }
    @Test fun unsafeEmbedsAreRejectedAndLanguageIsPreserved() {
        val data = JSONArray("""[{"embedUrl":"javascript:alert(1)"},{"embedUrl":"https://user:password@example.com/embed"},
            {"embedUrl":"https://embed.example/watch/1","language":"French","source":"alpha","hd":true}]""")
        val servers = StreamedLive.parseServers(data)
        assertEquals(1, servers.size)
        assertTrue(servers.single().name.contains("French"))
        assertTrue(servers.single().name.contains("HD"))
    }
}
