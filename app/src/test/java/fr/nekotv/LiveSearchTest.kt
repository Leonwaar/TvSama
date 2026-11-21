package fr.nekotv

import com.streamflixreborn.streamflix.providers.DirectEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveSearchTest {
    private fun event(title: String, competition: String = "Streamed · Sport") =
        DirectEvent("event:$title", title, competition, 0, true, "https://example.test/$title")

    @Test fun blankSearchKeepsEveryLiveAndChannel() {
        val events = listOf(event("France 2"), event("Arte"))
        assertEquals(events, filterLiveEvents(events, ""))
        assertEquals(events, filterLiveEvents(events, "   "))
    }

    @Test fun searchMatchesChannelsAndEventsIgnoringAccentsAndCase() {
        val events = listOf(
            event("France 2", "TV française · Généraliste"),
            event("Arte", "TV française · Culture"),
            event("Ligue 1 · PSG – Lyon", "Streamed · Football")
        )
        assertEquals(listOf(events[0]), filterLiveEvents(events, "france"))
        assertEquals(listOf(events[2]), filterLiveEvents(events, "psg lyon"))
        assertEquals(listOf(events[2]), filterLiveEvents(events, "football"))
        assertTrue(filterLiveEvents(events, "inexistant").isEmpty())
    }
}
