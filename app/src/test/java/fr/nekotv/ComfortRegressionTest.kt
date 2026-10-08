package fr.nekotv

import android.app.Application
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ComfortRegressionTest {
    @Test fun qualityNeverComesFromOpaqueUrlDigitsAndDisplaysDecodedResolution() {
        assertEquals("Auto", declaredQuality("Coflix 4k7opaque720token"))
        assertEquals("Auto", declaredQuality("serveur1080unknown"))
        assertEquals("4K", declaredQuality("Serveur UHD VF"))
        assertEquals("1080p", declaredQuality("Lecteur 1080p VF"))
        assertEquals("720p", actualQuality(1280, 720))
        assertEquals("1080p", actualQuality(1920, 1080))
    }
    private val context get() = RuntimeEnvironment.getApplication()
    @Before fun clearTimer() {
        context.getSharedPreferences("tvsama_settings", 0).edit().clear().commit()
        context.getSharedPreferences("tvsama_sleep_timer", 0).edit().clear().commit()
    }
    @Test fun timerKeepsDeadlineAcrossPlayersAndRequiresResumeAfterExpiry() {
        var time = 1000L
        val first = SleepTimer(context) { time }
        first.configure(90)
        val deadline = first.begin()
        time += 20 * 60_000
        val nextEpisode = SleepTimer(context) { time }
        assertEquals(deadline, nextEpisode.begin())
        time = deadline
        assertTrue(nextEpisode.expired())
        assertEquals(deadline, SleepTimer(context) { time }.begin())
        assertEquals(time + 90 * 60_000, nextEpisode.restart())
        assertFalse(nextEpisode.expired())
        nextEpisode.configure(0)
        assertEquals(0L, nextEpisode.begin())
    }
    @Test fun everyTimerDurationExpiresExactlyAndStaysExpiredAcrossPlayers() {
        for (minutes in SleepTimer.options) {
            var time = 1000L
            val timer = SleepTimer(context) { time }
            timer.configure(minutes)
            val deadline = timer.begin()
            assertEquals(time + minutes * 60_000L, deadline)
            time = deadline - 1
            assertFalse(timer.expired())
            time = deadline
            assertTrue(timer.expired())
            val replacementPlayerTimer = SleepTimer(context) { time }
            assertEquals(deadline, replacementPlayerTimer.begin())
            assertTrue(replacementPlayerTimer.expired())
            time += 60_000
            assertTrue(replacementPlayerTimer.expired())
            replacementPlayerTimer.configure(0)
            assertEquals(0L, replacementPlayerTimer.begin())
            assertFalse(replacementPlayerTimer.expired())
        }
    }
    @Test fun multilingualAliasesMergeIntoOneFilmAndKeepRemakesSeparate() {
        fun film(title: String, year: Int, id: String, aliases: List<String> = emptyList()) =
            Anime(title, "Film", emptyList(), id, provider = id, year = year, aliases = aliases)
        val merged = CatalogIdentity.merge(listOf(
            film("Le Voyage de Chihiro", 2001, "fr"),
            film("Spirited Away", 2001, "en"),
            film("千と千尋の神隠し", 2001, "ja")))
        assertEquals(1, merged.size)
        assertEquals(3, merged.single().references.size)
        assertEquals(2, CatalogIdentity.merge(listOf(
            film("Dune", 1984, "old", listOf("デューン")),
            film("Dune", 2021, "new", listOf("デューン")))).size)
        assertNotEquals(CatalogIdentity.title("映画甲"), CatalogIdentity.title("映画乙"))
    }
    @Test fun discoveriesAppendAndEnrichWithoutMovingExistingPosters() {
        val first = Anime("Classroom of the Elite", "Série", emptyList(), "one", provider = "one")
        val second = first.copy(title = "Naruto", id = "two")
        val third = first.copy(title = "Bleach", id = "three")
        val result = CatalogIdentity.appendStable(listOf(first, second), listOf(third,
            first.copy(title = "ようこそ実力至上主義の教室へ", id = "jp", provider = "jp", poster = "poster")))
        assertEquals(listOf("one", "two", "three"), result.map { it.id })
        assertEquals("poster", result.first().poster)
        assertTrue(result.first().references.any { it.provider == "jp" })
    }
    @Test fun largeCatalogueRefreshPreservesEveryPositionAndAppendsNewPages() {
        val original = (1..2500).map { Anime("Titre $it", "Film", emptyList(), "$it", year = 2000 + it % 20) }
        val refresh = original.reversed().map { it.copy(provider = "nouvelle-source", id = "new-${it.id}", poster = "poster") }
        val extra = Anime("Découverte supplémentaire", "Film", emptyList(), "extra")
        val result = CatalogIdentity.appendStable(original, refresh + extra)
        assertEquals(original.map { it.id } + "extra", result.map { it.id })
        assertTrue(result.take(2500).all { it.poster == "poster" })
    }
    @Test fun movieResumeDoesNotInventSeasonOrEpisode() {
        val library = LibraryStore(context)
        val movie = Anime("Film test", "Film", emptyList(), "movie-test")
        library.save(movie, Episode("S1 E1", "", emptyList(), "movie-test", 1, 1), 10_000, 100_000)
        val saved = library.continuing().first { it.anime.id == movie.id }
        assertEquals(0, saved.season)
        assertEquals(0, saved.number)
        assertEquals("Film", saved.episodeTitle)
    }
    @Test fun liveValidationRejectsHtmlAndRecognizesContainers() {
        assertNull(mediaMimeType("<html><body>#EXTM3U fake video</body></html>".toByteArray()))
        assertNull(mediaMimeType("<!DOCTYPE html><title>Just a moment</title>".toByteArray()))
        assertEquals("application/x-mpegURL", mediaMimeType("\uFEFF\n#EXTM3U\n#EXT-X-TARGETDURATION:6".toByteArray()))
        assertEquals("application/dash+xml", mediaMimeType("<?xml version=\"1.0\"?><MPD></MPD>".toByteArray()))
        assertEquals("video/mp4", mediaMimeType(byteArrayOf(0, 0, 0, 24) + "ftypisom".toByteArray()))
        val ts = ByteArray(564).apply { this[0] = 0x47; this[188] = 0x47; this[376] = 0x47 }
        assertEquals("video/mp2t", mediaMimeType(ts))
    }
    @Test fun pausedScreenDimsAtThirtySecondsAndMovementWakesWithoutResuming() {
        var time = 1000L
        val dimming = PauseDimming { time }
        assertFalse(dimming.shouldDim(paused = true, enabled = true, lastInteraction = 1000))
        time = 30_999
        assertFalse(dimming.shouldDim(true, true, 1000))
        time = 31_000
        assertTrue(dimming.shouldDim(true, true, 1000))
        assertFalse(dimming.shouldDim(true, true, time))
        time += 30_000
        assertTrue(dimming.shouldDim(true, true, 31_000))
        assertFalse(dimming.shouldDim(true, false, 31_000))
        assertFalse(dimming.shouldDim(false, true, 31_000))
        assertFalse(dimming.shouldDim(true, true, 31_000))
    }
    @Test fun livePlaylistProbeFollowsVariantsAndChecksEncryptedSegmentReferences() {
        val master = hlsProbe("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1000000\n720/index.m3u8", "https://media.example/live/master.m3u8")
        assertEquals("https://media.example/live/720/index.m3u8", master.variant)
        assertNull(master.segment)
        val media = hlsProbe("""#EXTM3U
            #EXT-X-MAP:URI="init.mp4"
            #EXT-X-KEY:METHOD=AES-128,URI="../key"
            #EXTINF:6,
            segment.m4s
        """.trimIndent(), master.variant!!)
        assertEquals("https://media.example/live/720/init.mp4", media.initialization)
        assertEquals("https://media.example/live/key", media.key)
        assertEquals("https://media.example/live/720/segment.m4s", media.segment)
        assertNull(hlsProbe("#EXTM3U\n#EXT-X-TARGETDURATION:6", master.variant!!).segment)
        assertTrue(runCatching { hlsProbe("<html>captcha</html>", master.variant!!) }.isFailure)
        assertTrue(runCatching { hlsProbe("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1", master.variant!!) }.isFailure)
    }
    @Test fun originalAudioIsNotMisrepresentedAsFrenchSubtitles() {
        assertEquals("UNKNOWN", mediaLanguage("English Original VO", emptyList()))
        assertEquals("UNKNOWN", mediaLanguage("Japanese", listOf("English")))
        assertEquals("VOSTFR", mediaLanguage("Japanese", listOf("Français")))
        assertEquals("VF", mediaLanguage("Uqload VF", listOf("Français")))
        assertEquals("VOSTFR", mediaLanguage("Vidzy VOSTFR", emptyList()))
    }
}
