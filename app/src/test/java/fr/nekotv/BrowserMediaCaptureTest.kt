package fr.nekotv

import org.junit.Assert.*
import org.junit.Test

class BrowserMediaCaptureTest {
    @Test fun keepsSignedMediaAndOnlyTheHeadersNeededByPlayback() {
        val candidate = browserMediaCandidate("https://cdn.example/master.m3u8?token=a%2Fb",
            mapOf("referer" to "https://player.example/embed/1", "Cookie" to "session=ok", "Authorization" to "private", "Range" to "bytes=0-1"),
            "https://source.example/episode", "AnimeKO")!!
        assertEquals("https://cdn.example/master.m3u8?token=a%2Fb", candidate.url)
        assertEquals("https://player.example/embed/1", candidate.headers["referer"])
        assertEquals("session=ok", candidate.headers["Cookie"])
        assertFalse(candidate.headers.containsKey("Authorization"))
        assertFalse(candidate.headers.containsKey("Range"))
        assertEquals("application/x-mpegURL", candidate.mimeType)
        assertFalse(candidate.reachable)
    }
    @Test fun rejectsEmbedPagesBlobUrlsAndSegments() {
        for (url in listOf("https://site.example/embed/1", "blob:https://site.example/id", "https://cdn.example/segment.ts", "file:///tmp/video.mp4"))
            assertNull(browserMediaCandidate(url, emptyMap(), "https://site.example/", "Source"))
        assertEquals("video/mp4", browserMediaCandidate("https://cdn.example/movie.mp4", emptyMap(), "https://site.example/", "Source")!!.mimeType)
    }
}
