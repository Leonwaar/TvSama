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
    @Test fun coldStartAndEmptyCatalogueRemainUsable() {
        val items = listOf(item("Populaire"), item("Deuxième"), item("Troisième"))
        assertEquals(items.first(), Recommendations.rank(items, emptyList(), emptyList(), emptyList()).first())
        assertTrue(Recommendations.rank(emptyList(), emptyList(), emptyList(), listOf("anime")).isEmpty())
    }
}
