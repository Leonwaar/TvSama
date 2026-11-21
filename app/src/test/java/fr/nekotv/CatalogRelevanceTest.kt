package fr.nekotv

import org.junit.Assert.*
import org.junit.Test

/** Search relevance: titles, aliases, genres, authors/directors and popularity tiebreaks. */
class CatalogRelevanceTest {
    private fun card(title: String, id: String = title, aliases: List<String> = emptyList(),
        genres: List<String> = emptyList(), description: String = "",
        localizedTitles: Map<String, String> = emptyMap(), year: Int? = null) =
        Anime(title, "Série", emptyList(), id, description = description, genres = genres,
            aliases = aliases, localizedTitles = localizedTitles, year = year)

    @Test fun genreQueryRanksCategoryMatchesAbovePopularUnrelatedTitles() {
        val action = card("Braquo", genres = listOf("Action", "Thriller"))
        val popular = card("Autre chose")
        val ranked = rankCatalogue(listOf(popular, action), "action", mapOf(CatalogIdentity.title("Autre chose") to 9999.0))
        assertEquals("Braquo", ranked.first().title)
    }

    @Test fun multiWordGenreAnswersItsCategory() {
        val scienceFiction = card("Série SF", genres = listOf("Science-Fiction"))
        val popular = card("Autre chose")
        val ranked = rankCatalogue(listOf(popular, scienceFiction), "science fiction", mapOf(CatalogIdentity.title("Autre chose") to 9999.0))
        assertEquals("Série SF", ranked.first().title)
    }

    @Test fun authorWordsInDescriptionOutrankPopularUnrelatedTitles() {
        val directed = card("Le château", description = "Réalisé par Hayao Miyazaki")
        val popular = card("Autre chose")
        val ranked = rankCatalogue(listOf(popular, directed), "hayao miyazaki", mapOf(CatalogIdentity.title("Autre chose") to 9999.0))
        assertEquals("Le château", ranked.first().title)
    }

    @Test fun everyQueryTokenMustAppearInTheTitleToOutrankPartialMatches() {
        val full = card("Hunter x Hunter: The Last Mission")
        val partial = card("Hunter x Hunter")
        val ranked = rankCatalogue(listOf(partial, full), "last mission", mapOf(CatalogIdentity.title("Hunter x Hunter") to 9999.0))
        assertEquals(full.title, ranked.first().title)
    }

    @Test fun aliasVariantQueryMeetsTheSameCard() {
        val titan = card("Attack on Titan", aliases = listOf("Shingeki no Kyojin", "進撃の巨人"))
        val popular = card("Autre chose")
        val ranked = rankCatalogue(listOf(popular, titan), "shingeki no kyojin", mapOf(CatalogIdentity.title("Autre chose") to 9999.0))
        assertEquals("Attack on Titan", ranked.first().title)
    }

    @Test fun localizedFrenchTitleAnswersAFrenchQuery() {
        val titan = card("Attack on Titan", localizedTitles = mapOf("fr" to "L’Attaque des Titans"))
        val popular = card("Autre chose")
        val ranked = rankCatalogue(listOf(popular, titan), "attaque des titans", mapOf(CatalogIdentity.title("Autre chose") to 9999.0))
        assertEquals("Attack on Titan", ranked.first().title)
    }

    @Test fun editionYearInQueryPrefersTheSameEdition() {
        val old = card("Hunter x Hunter", id = "1999", year = 1999)
        val recent = card("Hunter x Hunter", id = "2011", year = 2011)
        val ranked = rankCatalogue(listOf(old, recent), "hunter x hunter 2011")
        assertEquals("2011", ranked.first().id)
    }

    @Test fun popularityStillBreaksTiesAndOrdersBlankQueries() {
        val show = card("Show")
        val popular = card("Popular")
        val ranked = rankCatalogue(listOf(show, popular), "", mapOf(CatalogIdentity.title("Popular") to 50.0))
        assertEquals("Popular", ranked.first().title)
    }

    @Test fun popularityLookupFollowsMergedIdentityTitles() {
        val edition = card("Hunter x Hunter", id = "2011", year = 2011)
        val other = card("Autre chose")
        val ranked = rankCatalogue(listOf(other, edition), "", mapOf(CatalogIdentity.title("Hunter x Hunter") to 50.0))
        assertEquals("2011", ranked.first().id)
    }

    @Test fun unmatchedWordsStayLast() {
        val matching = card("Naruto", description = "Un ninja")
        val unrelated = card("Autre chose", description = "Rien à voir")
        val ranked = rankCatalogue(listOf(unrelated, matching), "naruto")
        assertEquals("Naruto", ranked.first().title)
        assertTrue(ranked.last().title == "Autre chose")
    }

    @Test fun searchGateRejectsPopularUnrelatedCards() {
        val results = listOf(
            card("Cool Attitude", id = "cool"),
            card("Mother Witch", id = "witch"),
            card("The Desinvited", id = "desinvited"),
            card("One Piece", id = "one-piece"),
            card("My Cool Attitude", id = "match", aliases = listOf("Cool Attitude"))
        )
        val filtered = results.filter { isCatalogueMatch(it, "cool attitude") }
        assertEquals(listOf("cool", "match"), filtered.map { it.id })
    }

    @Test fun searchGateKeepsGenreAndDescriptionQueries() {
        val drama = card("Une série", genres = listOf("Drame"))
        val director = card("Un film", description = "Réalisé par Jane Doe")
        assertTrue(isCatalogueMatch(drama, "drame"))
        assertTrue(isCatalogueMatch(director, "jane doe"))
    }
}
