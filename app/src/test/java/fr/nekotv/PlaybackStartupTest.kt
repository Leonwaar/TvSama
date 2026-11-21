package fr.nekotv

import android.app.Application
import android.content.Context
import com.streamflixreborn.streamflix.StreamFlixApp
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PlaybackStartupTest {
    @Test fun healthMessagesExplainTheProblemWithoutExposingTheRemoteResponse() {
        org.junit.Assert.assertTrue(sourceFailureStatus("HTTP 403 secret-token").contains("Accès refusé"))
        org.junit.Assert.assertTrue(sourceFailureStatus("captcha required").contains("Accès"))
        org.junit.Assert.assertTrue(sourceFailureStatus("HTTP 404").contains("actualisez"))
        org.junit.Assert.assertTrue(sourceFailureStatus("HTTP 429").contains("réessayez"))
        org.junit.Assert.assertFalse(sourceFailureStatus("remote stack secret-token").contains("secret-token"))
    }
    @Test fun publishedMatroskaFilesAreRecognizedButAnHtmlDownloadPageIsNot() {
        org.junit.Assert.assertEquals("video/x-matroska", mediaMimeType(byteArrayOf(0x1a, 0x45, 0xdf.toByte(), 0xa3.toByte())))
        org.junit.Assert.assertNull(mediaMimeType("<html>download film.mkv</html>".toByteArray()))
    }
    @Test fun shortPromoCannotBeAcceptedAsAMovieOrEpisode() {
        assertNotNull(programDurationIssue("Film", 30_000))
        assertNotNull(programDurationIssue("Série", 30_000))
        assertNull(programDurationIssue("Film", 90_000))
        assertNull(programDurationIssue("Direct", 30_000))
        assertNull(programDurationIssue("Flux personnel", 30_000))
        assertNotNull(programDurationIssue("Film", -1))
        assertNotNull(programDurationIssue("Série", -1))
        val teaser = "#EXTM3U\n#EXTINF:10,\na.ts\n#EXTINF:20,\nb.ts\n#EXT-X-ENDLIST"
        org.junit.Assert.assertEquals(30_000L, hlsVodDurationMs(teaser))
        assertNull(hlsVodDurationMs("#EXTM3U\n#EXTINF:10,\na.ts"))
    }
    @Test fun iptvWindowIsNotMistakenForAShortSeriesEpisode() {
        assertNull(programDurationIssue("Série", 30_000, live = true))
        assertNull(programDurationIssue("Série", androidx.media3.common.C.TIME_UNSET, live = true))
        org.junit.Assert.assertTrue(Anime("Chaîne", "Série", emptyList(), provider = "Pluto TV FR").isLiveContent())
        org.junit.Assert.assertFalse(Anime("Épisode", "Série", emptyList()).isLiveContent())
    }
    @Test fun unlabeledReadersAreNotOfferedAsFrenchAudio() {
        org.junit.Assert.assertEquals("UNKNOWN", mediaLanguage("vidzy.org", emptyList()))
        org.junit.Assert.assertFalse(acceptsMediaLanguage("VF", "UNKNOWN"))
        org.junit.Assert.assertFalse(acceptsMediaLanguage("VOSTFR", "UNKNOWN"))
        org.junit.Assert.assertFalse(acceptsMediaLanguage("VF", "VOSTFR"))
        org.junit.Assert.assertFalse(acceptsMediaLanguage("VOSTFR", "VF"))
        org.junit.Assert.assertTrue(acceptsMediaLanguage("VF", "VF"))
        org.junit.Assert.assertTrue(acceptsMediaLanguage("VOSTFR", "VOSTFR"))
    }
    @Test fun rejectedClipCannotMakeTheSourceLookVerified() {
        val app = StreamFlixApp()
        ReflectionHelpers.callInstanceMethod<Void>(app, "attach",
            ReflectionHelpers.ClassParameter.from(Context::class.java, RuntimeEnvironment.getApplication()))
        StreamFlixApp::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, app)
        com.streamflixreborn.streamflix.utils.UserPreferences.setup(app)
        val manager = StreamFlixProviderManager.getInstance()
        val clip = VideoSource("Uqload", "https://media.example/promo.mp4", "VF", "Auto", "DuréeTest")
        manager.rejectShortStream(clip)
        manager.confirmProgramPlayback(clip, 120_000)
        org.junit.Assert.assertTrue(manager.sourceHealth()[clip.provider].orEmpty().startsWith("⚠"))
        val movie = clip.copy(name = "Autre lecteur", url = "https://media.example/film.mp4")
        org.junit.Assert.assertEquals(listOf(movie), manager.rankSources(listOf(clip, movie)))
        val uqload = clip.copy(url = "https://media.example/other.mp4", quality = "4K", latencyMs = 1)
        org.junit.Assert.assertEquals(movie, manager.rankSources(listOf(uqload, movie)).first())
        val anotherSite = movie.copy(provider = "Autre site", url = "https://other.example/full.mp4", latencyMs = 50)
        val firstMirror = movie.copy(latencyMs = 1)
        val secondMirror = movie.copy(url = "https://media.example/mirror.mp4", latencyMs = 2)
        org.junit.Assert.assertEquals(2, manager.rankSources(listOf(firstMirror, secondMirror, anotherSite))
            .take(2).map { it.provider }.distinct().size)
        manager.confirmProgramPlayback(movie, 120_000)
        org.junit.Assert.assertTrue(manager.sourceHealth()[clip.provider].orEmpty().startsWith("✓"))
    }
    @Test fun playerAcceptsTheSameUnescapedMediaAddressesAsTheNetworkProbe() {
        val app = StreamFlixApp()
        ReflectionHelpers.callInstanceMethod<Void>(app, "attach",
            ReflectionHelpers.ClassParameter.from(Context::class.java, RuntimeEnvironment.getApplication()))
        StreamFlixApp::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, app)
        com.streamflixreborn.streamflix.utils.UserPreferences.setup(app)
        val address = "https://cdn.example/Mon film [VF].mp4?token=a|b"
        assertNotNull(okhttp3.Request.Builder().url(address).build())
        assertNotNull(MediaNetwork.factory(VideoSource("Test", address, "VF", "Auto", "Test")))
    }
}
