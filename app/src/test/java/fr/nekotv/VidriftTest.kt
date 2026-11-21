package fr.nekotv

import android.app.Application
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class VidriftTest {
    private val film = Anime("Inception", "Film", emptyList(), tmdbId = 27205, originalLanguage = "en")

    @Test fun onlyFrenchAndFrenchSubtitledModesAreExposedAndNeverMarkedVerifiedBeforePlayback() {
        val sources = listOf(vidriftSources(film, null, "VF").single(), vidriftSources(film, null, "VOSTFR").single())
        assertEquals(listOf("VF", "VOSTFR"), sources.map { it.language })
        assertTrue(sources.all { !it.reachable && it.mimeType == VIDRIFT_MIME_TYPE })
        assertTrue(vidriftSources(film, null, "Toutes").isEmpty())
        assertTrue(vidriftSources(film, null, "VO").isEmpty())
        assertTrue(vidriftSources(film.copy(originalLanguage = null), null, "VOSTFR").isEmpty())
    }

    @Test fun unknownAndConflictingTitleIdsNeverProduceALink() {
        assertTrue(vidriftSources(film.copy(tmdbId = null), null, "VF").isEmpty())
        val conflict = film.copy(references = listOf(MediaReference("WarFlix", "https://warflix.im/film/123", "Film")))
        assertTrue(vidriftSources(conflict, null, "VF").isEmpty())
    }

    @Test fun embedOriginFollowsTheValidatedOfficialSiteAddress() {
        assertEquals("https://embed.vidrift.in", vidriftOriginFor("https://vidrift.in/"))
        assertEquals(VIDRIFT_ORIGIN, vidriftOriginFor("https://vidrift.net/"))
        assertEquals(VIDRIFT_ORIGIN, vidriftOriginFor("https://evil.example/"))
        assertEquals(VIDRIFT_ORIGIN, vidriftOriginFor("http://vidrift.in/"))
    }

    @Test fun episodesKeepTheirConfirmedSeasonAndEpisodeIdentity() {
        val show = film.copy(tag = "Série", tmdbId = 1429, originalLanguage = "ja")
        val episode = Episode("Épisode", "", emptyList(), number = 25, seasonNumber = 2, introSeason = 2, introNumber = 1)
        val source = vidriftSources(show, episode, "VOSTFR").single()
        val config = vidriftConfig(source, 15000, true)!!
        assertEquals("1429", config.getString("tmdbId"))
        assertEquals(2, config.getInt("season")); assertEquals(1, config.getInt("episode"))
        assertEquals("ja", config.getString("originalLanguage"))
        assertEquals(15.0, config.getDouble("resumeAt"), 0.0)
        assertNull(vidriftConfig(source.copy(url = source.url.replace("embed.vidrift.net", "evil.example")), 0, false))
        assertNull(vidriftConfig(source.copy(url = source.url.replace("https://", "http://")), 0, false))
        assertNull(vidriftConfig(source.copy(language = "VO"), 0, false))
    }

    @Test fun confirmedNativeReadersRemainAheadOfEveryUnconfirmedEmbed() {
        val native = (1..3).map { VideoSource("Native $it", "https://media.example/$it.m3u8", "VF", "720p", "Site") }
        val embed = vidriftSources(film, null, "VF").single()
        val fallback = VideoSource("Lecteur non confirmé", "https://site.example/player/1", "VF", "Auto", "Site",
            reachable = false)
        assertEquals(native + fallback + embed, StreamFlixProviderManager.getInstance().rankSources(listOf(fallback, embed) + native))
    }
    @Test fun officialEmbedKeepsEpisodeNextAndSkipControlsAvailable() {
        val hidden = Uri.parse(vidriftSources(film, null, "VF").single().url).getQueryParameter("hide").orEmpty()
        assertFalse(hidden.contains("episodes"))
        assertFalse(hidden.contains("next"))
        assertFalse(hidden.contains("skip"))
        assertFalse(hidden.contains("volume"))
        assertFalse(hidden.contains("lock"))
        assertFalse(hidden.contains("episodes"))
        assertFalse(hidden.contains("next"))
    }
    @Test fun animeEmbedUsesExactDynamicAniListMatchAndAbsoluteEpisodeNumber() {
        val anime = Anime("Classroom of the Elite", "Série", listOf(
            Episode("S1E1", "", emptyList(), "s1e1", 1, 1),
            Episode("S1E2", "", emptyList(), "s1e2", 2, 1),
            Episode("S2E1", "", emptyList(), "s2e1", 1, 2)
        ), provider = "Animes-Sama", year = 2017)
        val candidates = listOf(
            AniListTitle(1, listOf("Classroom of the Elite"), emptyList(), 2017, 12, "JP"),
            AniListTitle(2, listOf("Classroom of the Elite"), emptyList(), 2022, 13, "JP")
        )
        assertEquals(1, selectAniListTitle(anime, candidates)?.id)
        assertNull(selectAniListTitle(anime.copy(title = "Unmatched Anime"), candidates))
        assertEquals(3, vidriftAniListEpisode(anime, anime.episodes.last()))
        val source = vidriftAniListSource(anime, anime.episodes.last(), candidates.first(), "VOSTFR")!!
        assertTrue(source.url.contains("/embed/anilist/1/3"))
        val config = vidriftConfig(source, 0, false)!!
        assertEquals("anilist", config.getString("mediaType"))
        assertEquals("1", config.getString("aniListId"))
        assertEquals(3, config.getInt("episode"))
    }
    @Test fun aYearInTheTitleSelectsTheRightAniListEditionWithoutAReleaseYear() {
        val anime = Anime("Hunter x Hunter 2011", "Série", emptyList(), "provider",
            provider = "Animes-Sama", aliases = listOf("Hunter x Hunter"))
        val candidates = listOf(
            AniListTitle(136, listOf("Hunter x Hunter"), emptyList(), 1999, 62, "JP"),
            AniListTitle(11061, listOf("Hunter x Hunter (2011)", "Hunter x Hunter"), emptyList(), 2011, 148, "JP")
        )
        assertEquals(11061, selectAniListTitle(anime, candidates)?.id)
    }
    @Test fun metadataSearchDoesNotPickAnUnrelatedMovieReleasedInTheSameYear() {
        val candidates = org.json.JSONArray("""[{"id":1,"title":"Un autre film","release_date":"2010-01-01"},{"id":27205,"title":"Inception","release_date":"2010-07-16"}]""")
        assertEquals(27205, selectTmdbResult(film.copy(year = 2010), candidates)!!.getInt("id"))
        candidates.put(org.json.JSONObject("""{"id":2,"title":"Inception","release_date":"2010-01-01"}"""))
        assertNull(selectTmdbResult(film.copy(year = 2010), candidates))
    }

    @Test fun anExistingConfirmedCatalogueReferenceProvidesThePublicApiId() {
        val anime = film.copy(tmdbId = null, references = listOf(MediaReference("WarFlix", "https://warflix.im/film/27205", "Film")))
        assertEquals("27205", vidriftConfig(vidriftSources(anime, null, "VOSTFR").single(), 0, false)!!.getString("tmdbId"))
    }

    @Test fun audioLabelsAreConfirmedAndNeverGuessed() {
        assertEquals("fr", vidriftAudioLanguage("French"))
        assertEquals("fr", vidriftAudioLanguage("VF"))
        assertEquals("en", vidriftAudioLanguage("English"))
        assertEquals("ja", vidriftAudioLanguage("Japanese"))
        assertNull(vidriftAudioLanguage("VOSTFR"))
        assertNull(vidriftAudioLanguage("Sous-titres français"))
        assertTrue(vidriftAcceptsAudio("VF", "en", "Français"))
        assertFalse(vidriftAcceptsAudio("VF", "en", "English"))
        assertTrue(vidriftAcceptsAudio("VOSTFR", "ja", "Japanese"))
        assertFalse(vidriftAcceptsAudio("VOSTFR", "ja", "English"))
        assertFalse(vidriftAcceptsAudio("VOSTFR", null, "English"))
        assertFalse(vidriftAcceptsAudio("VOSTFR", "fr", "French"))
    }

    @Test fun vostfrIsNotOfferedWithoutAConfirmedNonFrenchOriginal() {
        val show = film.copy(tag = "Série", tmdbId = 1429, originalLanguage = null)
        val episode = Episode("Épisode", "", emptyList(), number = 1, seasonNumber = 1)
        assertTrue(vidriftSources(show, episode, "VOSTFR").isEmpty())
        assertEquals(listOf("VF"), vidriftSources(show, episode, "VF").map { it.language })
        val anime = Anime("Série", "Série", listOf(episode), provider = "Animes-Sama")
        val title = AniListTitle(1, listOf("Série"), emptyList(), 2020, 12, null)
        assertTrue(vidriftAniListSources(anime, episode, title, "VOSTFR").isEmpty())
    }
}
