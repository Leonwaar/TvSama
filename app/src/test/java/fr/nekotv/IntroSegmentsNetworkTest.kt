package fr.nekotv

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class IntroSegmentsNetworkTest {
    @Test(timeout = 60_000) fun reportedShowsHavePublishedSegments() = runBlocking {
        assumeTrue(System.getProperty("tvsama.networkAudit") == "true")
        val cases = listOf(
            Triple("Grey's Anatomy", "tt0413573", 1), Triple("Grey's Anatomy", "tt0413573", 2),
            Triple("Cyberpunk: Edgerunners", "tt12590266", 1), Triple("Cyberpunk: Edgerunners", "tt12590266", 2),
            Triple("Hunter x Hunter", "tt2098220", 1))
        val lines = mutableListOf("# Intro/outro — ${java.time.Instant.now()}", "")
        for ((title, imdb, episode) in cases) {
            val segments = fetchSegments(imdb, 1, episode)
            lines += "- $title S1E$episode : intro=${segments?.intro}, outro=${segments?.outro}."
            assertNotNull("Aucun outro publié pour $title", segments?.outro)
            if (episode == 2 || title == "Hunter x Hunter") assertNotNull(segments?.intro)
            if (title == "Cyberpunk: Edgerunners" && episode == 1) assertNull("IntroDB marque cet épisode sans intro", segments?.intro)
        }
        File("../verification/INTRO_OUTRO_NETWORK.md").writeText(lines.joinToString("\n") + "\n")
    }
}
