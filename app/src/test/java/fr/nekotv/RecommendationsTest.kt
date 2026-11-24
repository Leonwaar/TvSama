package fr.nekotv

import org.junit.Assert.*
import org.junit.Test

class RecommendationsTest {
    private fun item(title: String, tag: String = "Film", genres: List<String> = emptyList()) = Anime(title, tag, emptyList(), title, genres = genres)
    @Test fun searchAliasesTransferInterestsToOtherFormats() {
        val searched = item("Saiki", "Série", listOf("Animation", "Comédie", "Fantastique")).copy(aliases = listOf("Disastrous life"))
        val drama = item("Procès", genres = listOf("Drame"))
        val comedy = item("Super comédie", genres = listOf("Comédie", "Fantastique"))
        val result = Recommendations.rank(listOf(drama, comedy), emptyList(), emptyList(), listOf("Disastrous life"), listOf(searched))
        assertEquals(comedy, result.first())
    }
    @Test fun recentEngagedViewingOutweighsOldViewing() {
        val now = 2_000_000_000_000L
        val funny = item("Rires", genres = listOf("Comédie"))
        val scary = item("Frissons", genres = listOf("Horreur"))
        val history = listOf(SavedPlayback(item("Ancien", genres = listOf("Horreur")), "", "", 100, 100, updatedAt = now - 365L * 86_400_000),
            SavedPlayback(item("Récent", genres = listOf("Comédie")), "", "", 90, 100, updatedAt = now))
        assertEquals(funny, Recommendations.rank(listOf(scary, funny), history, emptyList(), emptyList(), now = now).first())
    }
    @Test fun favoriteGenresAndDiscoveryMixDoNotTrapUserInAnimation() {
        val anime = (1..12).map { item("Anime $it", "Série", listOf("Animation", "Action")) }
        val films = (1..6).map { item("Film $it", "Film", listOf("Action")) }
        val ranked = Recommendations.rank(anime + films, emptyList(), listOf(anime.first()), listOf("anime"))
        assertTrue(ranked.take(6).any { it.tag == "Film" })
        assertEquals((anime + films).toSet(), ranked.toSet())
        assertEquals(ranked.size, ranked.distinct().size)
    }
    @Test fun wordsInSynopsisDriveRelevanceWithoutGenreMetadata() {
        val unrelated = item("Paysage").copy(description = "Une famille prépare un voyage tranquille à la plage.")
        val mystery = item("Secrets").copy(description = "Un détective mène une enquête sur un meurtre et résout le mystère.")
        val favorite = item("Affaire", "Série").copy(description = "Une enquêtrice enquête sur un meurtre mystérieux.")
        assertEquals(mystery, Recommendations.rank(listOf(unrelated, mystery), emptyList(), listOf(favorite), emptyList()).first())
    }
    @Test fun realPopularityAndReleaseDateBeatObscureOldCataloguePosition() {
        val now = java.time.Instant.parse("2026-10-15T12:00:00Z").toEpochMilli()
        val old = item("Ancien animé", "Série", listOf("Animation")).copy(year = 1985, popularity = 0.5, rating = 3.0, voteCount = 500)
        val recent = item("Succès récent").copy(year = 2026, popularity = 300.0, rating = 8.0, voteCount = 1500)
        assertEquals(recent, Recommendations.rank(listOf(old, recent), emptyList(), emptyList(), emptyList(), now = now).first())
    }
    @Test fun viewedTitlesStayBehindUnseenDiscoveriesEvenWhenPopular() {
        val seen = item("Déjà vu").copy(year = 2026, popularity = 999.0, rating = 9.0, voteCount = 1000)
        val unseen = item("À essayer").copy(year = 2025)
        val history = listOf(SavedPlayback(seen, "", "", 100, 100, updatedAt = System.currentTimeMillis()))
        assertEquals(unseen, Recommendations.rank(listOf(seen, unseen), history, listOf(seen), emptyList()).first())
    }
    @Test fun explorationCanChoosePopularContentOutsideThePreferredTheme() {
        val profile = item("Magie", "Série", listOf("Fantasy"))
        val niche = (1..60).map { item("Fantasy $it", "Série", listOf("Fantasy")) }
        val hit = item("Succès aventure", "Film", listOf("Adventure")).copy(year = 2026, popularity = 200.0, voteCount = 1000, rating = 8.0)
        val result = Recommendations.rank(niche + hit, emptyList(), listOf(profile), emptyList())
        assertTrue(result.take(5).contains(hit))
    }
    @Test fun mergingSourcesKeepsActualPopularityGenresAndRatings() {
        val first = item("Film").copy(provider = "Coflix", year = 2026)
        val ranked = first.copy(provider = "WarFlix", popularity = 120.0, rating = 8.0, voteCount = 1000, genres = listOf("Action"))
        val merged = CatalogIdentity.appendStable(listOf(first), listOf(ranked)).single()
        assertEquals(120.0, merged.popularity, 0.0)
        assertEquals(8.0, merged.rating, 0.0)
        assertEquals(1000, merged.voteCount)
        assertEquals(listOf("Action"), merged.genres)
    }
    @Test fun firstCardsRenderBeforeRankingAndOldCategoriesCannotLeak() {
        val first = item("Premier")
        val second = item("Deuxième")
        assertEquals(listOf(first, second), Recommendations.displayed(listOf(first, second), emptyList()))
        assertEquals(listOf(first, second), Recommendations.displayed(listOf(first, second, first), listOf(first, first)))
        assertEquals(listOf(second, first), Recommendations.displayed(listOf(first, second), listOf(item("Ancienne catégorie"), second)))
    }
    @Test fun laterRecommendationsAppendWithoutMovingTheFirstRelevantCards() {
        val first = item("Premier pertinent")
        val second = item("Deuxième pertinent")
        val late = item("Nouvelle découverte")
        val initial = Recommendations.displayed(listOf(second, first), listOf(first, second))
        val updated = Recommendations.displayed(listOf(late, second, first), listOf(late, second, first), initial)
        assertEquals(listOf(first.id, second.id, late.id), updated.map { it.id })
        assertEquals(updated, Recommendations.displayed(listOf(first, late, second), listOf(second, late, first), updated))
    }
    @Test fun laterSourcesEnrichTheFocusedCardWithoutReplacingItsIdentity() {
        val first = item("Film pertinent").copy(provider = "source-initiale")
        val other = item("Autre film")
        val merged = first.copy(provider = "source-arrivée-ensuite", id = "nouvel-id", poster = "affiche",
            references = listOf(MediaReference(first.provider, first.id, first.tag)))
        val updated = Recommendations.displayed(listOf(other, merged), listOf(other, merged), listOf(first))
        assertEquals(listOf(first.id, other.id), updated.map { it.id })
        assertEquals(first.provider, updated.first().provider)
        assertEquals("affiche", updated.first().poster)
        assertTrue(updated.first().references.any { it.provider == merged.provider })
    }
    @Test fun removedCardsAndOldCategoriesCannotStayInTheStableFeed() {
        val old = item("Ancienne catégorie")
        val first = item("Toujours présent")
        val extra = item("Nouveau")
        assertEquals(listOf(first.id, extra.id), Recommendations.displayed(listOf(extra, first), listOf(extra, first), listOf(old, first)).map { it.id })
    }
    @Test fun refreshKeepsExistingCardsWhileTheOtherSourcesAreStillLoading() {
        val first = item("Premier pertinent")
        val second = item("Deuxième pertinent")
        val extra = item("Nouveau résultat")
        val partial = Recommendations.displayed(listOf(second, extra), listOf(extra, second), listOf(first, second), loading = true)
        assertEquals(listOf(first.id, second.id, extra.id), partial.map { it.id })
        val completed = Recommendations.displayed(listOf(extra, first, second), listOf(second, extra, first), partial)
        assertEquals(partial.map { it.id }, completed.map { it.id })
    }
    @Test(timeout = 5000) fun largeCatalogueKeepsAllDiscoveriesWithoutUnboundedDiversification() {
        val catalogue = (1..1500).map { item("Film $it", genres = listOf("Action", "Adventure")).copy(description = "Une aventure et une enquête à travers le monde.", year = 2026, popularity = it.toDouble()) }
        val result = Recommendations.rank(catalogue, emptyList(), listOf(catalogue.first()), emptyList())
        assertEquals(catalogue.toSet(), result.toSet())
        assertEquals(catalogue.size, result.size)
    }
    @Test fun testMultilingualNormalizationAndDeduplication() {
        val chihiroFr = item("Le Voyage de Chihiro", "Film", emptyList()).copy(id = "1", provider = "fr", imdbId = "tt0245429")
        val chihiroEn = item("Spirited Away", "Film", emptyList()).copy(id = "2", provider = "en", imdbId = "tt0245429")
        val chihiroJp = item("千と千尋の神隠し", "Film", emptyList()).copy(id = "3", provider = "jp", imdbId = "tt0245429")

        val merged = CatalogIdentity.merge(listOf(chihiroFr, chihiroEn, chihiroJp))
        assertEquals(1, merged.size)
        assertEquals(3, merged.single().references.size)
    }

    @Test fun testExactMatchOverPopularity() {
        val popular = item("Generic Action Movie", "Film", emptyList()).copy(popularity = 1000.0)
        val exactMatch = item("Dune", "Film", emptyList()).copy(popularity = 10.0)
        
        val ranked = Recommendations.rankSearchResults(listOf(popular, exactMatch), "Dune")
        assertEquals(exactMatch, ranked.first())
    }
    
    @Test fun cinemaRowsFilterDeduplicateAndPrioritizeWeek() {
        val date = java.time.LocalDate.of(2026, 1, 10)
        fun film(id: Int, release: String) = item("Film $id").copy(tmdbId = id, poster = "poster", firstAired = release)
        val duplicate = film(1, "2026-01-12")
        val rows = CinemaReleaseRanking.partition(listOf(duplicate, film(1, "2026-02-01"), film(2, "2026-01-30")), date)
        assertEquals(listOf(1), rows[CinemaReleaseRanking.Row.WEEK].orEmpty().map { it.tmdbId })
        assertTrue(rows.values.flatten().distinctBy { it.tmdbId }.size == rows.values.flatten().size)
    }

    @Test fun cinemaRowsFilterMissingPoster() {
        val film = item("Film").copy(tmdbId = 1, firstAired = "2026-01-12")
        assertTrue(CinemaReleaseRanking.partition(listOf(film), java.time.LocalDate.of(2026, 1, 10)).values.flatten().isEmpty())
    }

    @Test fun releaseBadgesUseDeterministicFrenchLabels() {
        val today = java.time.LocalDate.of(2026, 1, 10)
        assertEquals("Aujourd'hui", releaseDateBadge("2026-01-10", today))
        assertEquals("Demain", releaseDateBadge("2026-01-11", today))
        assertEquals("Dans 7 jours", releaseDateBadge("2026-01-17", today))
        assertEquals("Bientôt", releaseDateBadge("2026-01-20", today))
        assertEquals("Au cinéma", releaseDateBadge("2026-01-01", today))
    }

    @Test fun testRankingLimitsAfterGrouping() {
        val list = (1..50).map { item("Title $it", "Film", emptyList()) }
        val ranked = Recommendations.rankSearchResults(list, "Title")
        // We set MAX_RESULTS to 20 in StreamFlixProviderManager
        assertTrue(ranked.size <= StreamFlixProviderManager.MAX_RESULTS)
    }
}
