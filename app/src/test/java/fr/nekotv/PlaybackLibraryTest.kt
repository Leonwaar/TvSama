package fr.nekotv

import android.app.Application
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PlaybackLibraryTest {
    private lateinit var store: LibraryStore
    private val episodes = listOf(Episode("Un", "", emptyList(), "1", 1, 1), Episode("Deux", "", emptyList(), "2", 2, 1), Episode("Trois", "", emptyList(), "3", 1, 2))
    private val show = Anime("Série test", "Série", episodes, "series", provider = "Test")
    @Before fun prepare() {
        RuntimeEnvironment.getApplication().getSharedPreferences("tvsama_library", 0).edit().clear().commit()
        store = LibraryStore(RuntimeEnvironment.getApplication())
    }
    @Test fun oneResumeCardButProgressRetainedForEachEpisode() {
        store.save(show, episodes[0], 100_000, 120_000)
        store.save(show, episodes[1], 40_000, 120_000)
        assertEquals(1, store.continuing().size)
        assertEquals(2, store.history().size)
        assertEquals(40_000L, store.progress(show, episodes[1]))
        assertEquals(1f, store.fraction(show, episodes[0]))
    }
    @Test fun correctedTitleAndRotatedAddressKeepTheSameMovieProgress() {
        val original = Anime("French-Stream", "Film", emptyList(), "https://old.example/film/doing-life.html", provider = "FrenchStreaming")
        val corrected = original.copy(title = "Doing Life", year = 2026, id = "https://new.example/film/doing-life.html")
        store.save(original, Episode("Film", "", emptyList(), original.id, 1, 1), 249_000, 6_500_000)
        assertEquals(249_000L, store.progress(corrected, Episode("Film", "", emptyList(), corrected.id, 1, 1)))
        store.save(corrected, Episode("Film", "", emptyList(), corrected.id, 1, 1), 251_000, 6_500_000)
        assertEquals(1, store.history().size)
        assertEquals(251_000L, store.progress(corrected, null))
    }
    @Test fun thresholdMovesToNextSeasonAndEpisode() {
        store.save(show, episodes[1], 90_000, 120_000)
        assertEquals(episodes[2], store.resumeEpisode(show))
        assertEquals(2, store.continuing().single().season)
        assertEquals("3", store.continuing().single().episodeId)
        assertEquals(0L, store.progress(show, episodes[2]))
    }
    @Test fun beforeThresholdKeepsTimecode() {
        store.save(show, episodes[1], 89_999, 120_000)
        assertEquals(episodes[1], store.resumeEpisode(show))
        assertEquals(89_999L, store.progress(show, episodes[1]))
    }
    @Test fun removalPreservesWatchedStateAndNewViewingRestoresCard() {
        store.save(show, episodes[0], 120_000, 120_000)
        store.hideResume(show)
        assertTrue(store.continuing().isEmpty())
        assertEquals(1f, store.fraction(show, episodes[0]))
        store.save(show, episodes[1], 5_000, 120_000)
        assertEquals(1, store.continuing().size)
    }
    @Test fun latestEngagementWinsEvenWhenRewatchingEarlierEpisode() {
        store.save(show, episodes[2], 40_000, 120_000)
        store.save(show, episodes[0], 20_000, 120_000)
        assertEquals(episodes[0], store.resumeEpisode(show))
    }
    @Test fun languageVariantsAndMissingYearMergeReferences() {
        val results = CatalogIdentity.merge(listOf(show.copy(title = "Série test VF", year = 2020), show.copy(title = "Serie test VOSTFR", id = "other", provider = "Other")))
        assertEquals(1, results.size)
        assertEquals(2, results.single().references.size)
    }
    @Test fun remakesRemainSeparate() {
        assertEquals(2, CatalogIdentity.merge(listOf(show.copy(year = 2020), show.copy(year = 1990))).size)
    }
    @Test fun nextEpisodeSkipsLanguageVariants() {
        val mixed = show.copy(episodes = listOf(episodes[0], episodes[0].copy(id = "vf"), episodes[1]))
        store.save(mixed, episodes[0], 120_000, 120_000)
        assertEquals(episodes[1], store.resumeEpisode(mixed))
        assertEquals("2", store.continuing().single().episodeId)
    }
    @Test fun searchHistoryIsUniqueAndBounded() {
        repeat(20) { store.rememberSearch("Recherche $it") }
        store.rememberSearch("recherche 19")
        assertEquals(12, store.searches().size)
        assertEquals("recherche 19", store.searches().first())
    }
    @Test fun semanticVersionComparison() {
        assertTrue(AppUpdates.newer("V1.0.100", "V1.0.70"))
        assertFalse(AppUpdates.newer("V1.0.69", "V1.0.70"))
        assertFalse(AppUpdates.newer("v1.0.70", "V1.0.70"))
        assertFalse(AppUpdates.newer("nightly", "V1.0.70"))
    }
    @Test fun introDbParsesMilliseconds() {
        val data = JSONObject("""{"imdb_id":"tt1234567","season":1,"episode":2,"start_ms":55000,"end_ms":145000}""")
        assertEquals(IntroSegment(55_000, 145_000), parseIntro(data, "tt1234567", 1, 2))
    }
    @Test fun introDbFallsBackToSeconds() {
        val data = JSONObject("""{"imdb_id":"tt1234567","season":1,"episode":2,"start_sec":55,"end_sec":145}""")
        assertEquals(IntroSegment(55_000, 145_000), parseIntro(data, "tt1234567", 1, 2))
    }
    @Test fun introDbRejectsWrongIdentityAndInvalidRange() {
        val data = JSONObject("""{"imdb_id":"tt1234567","season":1,"episode":2,"start_ms":145000,"end_ms":55000}""")
        assertNull(parseIntro(data, "tt1234567", 1, 2))
        assertNull(parseIntro(data, "tt7654321", 1, 2))
    }
    @Test fun introDbPreservesFractionalSecondsAndRejectsNullTimes() {
        val data = JSONObject("""{"imdb_id":"tt1234567","season":1,"episode":2,"start_sec":1.25,"end_sec":55.75}""")
        assertEquals(IntroSegment(1250, 55750), parseIntro(data, "tt1234567", 1, 2))
        data.put("end_sec", JSONObject.NULL)
        assertNull(parseIntro(data, "tt1234567", 1, 2))
    }

    @Test fun addressRotationsPreserveSavedPathsQueriesAndFragments() {
        val application = com.streamflixreborn.streamflix.StreamFlixApp()
        org.robolectric.util.ReflectionHelpers.callInstanceMethod<Void>(application, "attach",
            org.robolectric.util.ReflectionHelpers.ClassParameter.from(android.content.Context::class.java, RuntimeEnvironment.getApplication()))
        com.streamflixreborn.streamflix.StreamFlixApp::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, application)
        application.getSharedPreferences("source_addresses", 0).edit().clear().commit()
        val addresses = com.streamflixreborn.streamflix.utils.SourceAddresses
        assertTrue(addresses.update("Test", "https://old.example/catalog/", "https://new.example/anime/"))
        assertEquals("https://new.example/anime/show?q=vf#episode=2", addresses.rewrite("https://old.example/catalog/show?q=vf#episode=2"))
        assertEquals("https://new.example/catalogue/show", addresses.rewrite("https://old.example/catalogue/show"))
        assertTrue(addresses.update("Test", "https://new.example/anime/", "https://third.example/"))
        assertEquals("https://third.example/show?q=vf#episode=2", addresses.rewrite("https://old.example/catalog/show?q=vf#episode=2"))
        assertEquals("https://third.example/show", addresses.rewrite("https://new.example/anime/show"))
        assertEquals("https://cdn.example/image.jpg", addresses.rewrite("https://cdn.example/image.jpg"))
        assertFalse(addresses.update("Test", "https://third.example/", "http://unsafe.example/"))
        assertEquals("https://third.example/", addresses.current("Test", "fallback"))
        assertTrue(addresses.update("Endpoint", "https://endpoint.example/", "https://endpoint.example/token/home/site/"))
        assertEquals("https://endpoint.example/token/movie/123", addresses.rewrite("https://endpoint.example/token/movie/123"))
    }

    @Test fun favoriteSurvivesDetailsEnrichmentAndCanBeRemovedAfterReload() {
        val card = show.copy(title = "Série test VF", year = null)
        val details = card.copy(title = "Titre officiel", year = 2020, imdbId = "tt1234567")
        store.setFavorite(card, true)
        val reopened = LibraryStore(RuntimeEnvironment.getApplication())
        assertTrue(reopened.favorite(details))
        reopened.setFavorite(details, false)
        assertTrue(store.favorites().isEmpty())
    }

    @Test fun favoriteMatchesAlternateProviderReferencesAndRotatedDomains() {
        val original = show.copy(id = "https://old.example/show/42", provider = "Source")
        store.setFavorite(original, true)
        val rotated = original.copy(id = "https://new.example/show/42", title = "Titre corrigé", year = 2020)
        assertTrue(store.favorite(rotated))
        val alternate = rotated.copy(provider = "Autre", id = "other-id",
            references = listOf(MediaReference("Source", original.id, original.tag)))
        assertTrue(store.favorite(alternate))
        store.setFavorite(alternate, false)
        assertTrue(store.favorites().isEmpty())
    }

    @Test fun repeatedFavoriteActionsAreIdempotentAndRefreshMetadata() {
        store.setFavorite(show, true)
        store.setFavorite(show.copy(year = 2020), true)
        assertEquals(1, store.favorites().size)
        assertEquals(2020, store.favorites().single().year)
        store.setFavorite(show, false)
        store.setFavorite(show, false)
        assertTrue(store.favorites().isEmpty())
    }

    @Test fun favoritesKeepRemakesAndDifferentMediaSeparate() {
        val first = show.copy(id = "first", year = 1990, imdbId = "tt1234567")
        val remake = show.copy(id = "second", year = 2020, imdbId = "tt7654321")
        val movie = first.copy(tag = "Film")
        store.setFavorite(first, true)
        store.setFavorite(remake, true)
        store.setFavorite(movie, true)
        store.setFavorite(remake, false)
        assertEquals(2, store.favorites().size)
        assertTrue(store.favorite(first))
        assertTrue(store.favorite(movie))
        assertFalse(store.favorite(remake))
    }

    @Test fun removingLegacyFavoriteRemovesItsDuplicateEntries() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("tvsama_library", 0)
        store.setFavorite(show, true)
        val saved = org.json.JSONArray(prefs.getString("favorites", "[]"))
        saved.put(saved.getJSONObject(0))
        prefs.edit().putString("favorites", saved.toString()).commit()
        store.setFavorite(show.copy(title = "Titre enrichi", year = 2020), false)
        assertTrue(store.favorites().isEmpty())
    }

    @Test fun numberedSeasonsKeepEveryReferenceAndDoNotRenameUnrelatedNumbers() {
        val merged = CatalogIdentity.merge(listOf(
            show.copy(title = "Tokyo Revengers", id = "s1"),
            show.copy(title = "Tokyo Revengers 2", id = "s2"),
            show.copy(title = "Tokyo Revengers 3 (VF)", id = "s3"),
            show.copy(title = "Tokyo Revengers Saison 4 VOSTFR", id = "s4"),
            show.copy(title = "Brooklyn 99", id = "brooklyn")))
        assertEquals(2, merged.size)
        assertEquals(4, merged.first { it.title == "Tokyo Revengers" }.references.size)
        assertTrue(merged.any { it.title == "Brooklyn 99" })
        assertEquals(3, CatalogIdentity.seasonNumber("Tokyo Revengers 3 (VF)", "Tokyo Revengers"))
        assertEquals(2, CatalogIdentity.seasonNumber("Une série Season 2", "Une série"))
        assertNull(CatalogIdentity.seasonNumber("Brooklyn 99", "Brooklyn 99"))
    }

    @Test fun classroomAliasesAndSeasonsMergeWithoutLosingSources() {
        val result = CatalogIdentity.merge(listOf(
            show.copy(title = "Classroom of the Elite", year = 2017),
            show.copy(title = "Classroom Of The Elite Saison 2", year = 2022, id = "s2"),
            show.copy(title = "Classroom of Elite VF", id = "vf", provider = "Animes-Sama"),
            show.copy(title = "Youkoso Jitsuryoku Shijou Shugi no Kyoushitsu e", id = "japanese")))
        assertEquals(1, result.size)
        assertEquals(4, result.single().references.size)
        assertEquals(2017, result.single().year)
    }

    @Test fun introAndOutroAreIndependentAndIdentityChecked() {
        val json = JSONObject("""{"imdb_id":"tt1234567","season":1,"episode":1,"intro":{"start_ms":1000,"end_ms":90000},"outro":{"start_sec":1200.5,"end_sec":1300.25}}""")
        val segments = parseSegments(json, "tt1234567", 1, 1)!!
        assertEquals(IntroSegment(1000, 90000), segments.intro)
        assertEquals(IntroSegment(1200500, 1300250), segments.outro)
        assertNull(parseSegments(json, "tt1234567", 2, 1))
        json.put("intro", JSONObject.NULL)
        assertNull(parseSegments(json, "tt1234567", 1, 1)!!.intro)
        assertNotNull(parseSegments(json, "tt1234567", 1, 1)!!.outro)
    }

    @Test fun sharedResumeUsesNewestWatchEvenWhenItsTimecodeIsEarlier() {
        val first = SavedPlayback(show, "1", "Un", 90000, 200000, 1, 1, 100)
        val rewound = first.copy(position = 20000, updatedAt = 200)
        store.mergeHistory(listOf(first, rewound))
        assertEquals(1, store.history().size)
        assertEquals(20000L, store.progress(show, episodes[0]))
        store.mergeHistory(listOf(first))
        assertEquals(20000L, store.progress(show, episodes[0]))
    }
    @Test fun exactSearchMatchBeatsPopularUnrelatedTitles() {
        val unrelated = show.copy(title = "Other", id = "other")
        val exact = show.copy(title = "Naruto", id = "exact")
        val partial = show.copy(title = "Naruto Shippuden", id = "partial")
        val ranked = rankCatalogue(listOf(unrelated, partial, exact), "naruto", mapOf(CatalogIdentity.title("Other") to 9999.0))
        assertEquals(listOf("exact", "partial", "other"), ranked.map { it.id })
    }
    @Test fun homepageUsesPopularityWhenAvailable() {
        val popular = show.copy(title = "Popular", id = "popular")
        assertEquals("popular", rankCatalogue(listOf(show, popular), "", mapOf(CatalogIdentity.title("Popular") to 50.0)).first().id)
    }
    @Test fun seekStaysPreciseForFirstThreeSeconds() {
        assertEquals(1000L, seekIncrement(0))
        assertEquals(1000L, seekIncrement(2999))
        assertEquals(10000L, seekIncrement(3000))
        assertEquals(30000L, seekIncrement(6000))
        assertEquals(60000L, seekIncrement(10000))
    }

    @Test fun removingSharedResumeSurvivesSyncAndWatchingAgainRestoresIt() {
        store.save(show, episodes[0], 20000, 200000)
        val saved = store.history()
        store.hideResume(show)
        val hidden = store.hiddenResumeTimes()
        store.mergeHistory(saved)
        store.mergeHiddenResume(hidden)
        assertTrue(store.continuing().isEmpty())
        store.save(show, episodes[0], 21000, 200000)
        store.mergeHiddenResume(hidden)
        assertEquals(1, store.continuing().size)
    }

}
