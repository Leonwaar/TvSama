package fr.nekotv

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CinemaReleaseRankingTest {
    private val today = LocalDate.of(2026, 1, 10)
    private fun film(id: Int, date: String, poster: String = "poster", popularity: Double = 1.0) =
        Anime("Film $id", "Film", emptyList(), id.toString(), poster = poster, firstAired = date,
            tmdbId = id, popularity = popularity, rating = 8.0, voteCount = 100)

    @Test fun weekWinsAndDuplicateIsNotRepeated() {
        val rows = CinemaReleaseRanking.partition(listOf(film(1, "2026-01-11"), film(1, "2026-02-01"), film(2, "2026-02-01")), today)
        assertEquals(listOf(1), rows[CinemaReleaseRanking.Row.WEEK].orEmpty().map { it.tmdbId })
        assertEquals(listOf(2), rows[CinemaReleaseRanking.Row.UPCOMING].orEmpty().map { it.tmdbId })
    }

    @Test fun carouselNavigationClampsPagesAndKeyboardTargets() {
        assertEquals(0, CarouselNavigation.pageTarget(0, 6, 20, -1))
        assertEquals(6, CarouselNavigation.pageTarget(0, 6, 20, 1))
        assertEquals(19, CarouselNavigation.pageTarget(18, 6, 20, 1))
        assertEquals(2, CarouselNavigation.keyTarget(3, 10, CarouselKey.LEFT))
        assertEquals(4, CarouselNavigation.keyTarget(3, 10, CarouselKey.RIGHT))
    }

    @Test fun rowFailureIsRepresentableWithoutDroppingOtherRows() {
        val response = CinemaReleaseResponse("FR", "fr", listOf(
            CinemaReleaseJsonRow("week", "Cette semaine", emptyList(), "TMDB timeout"),
            CinemaReleaseJsonRow("now", "À l'affiche", listOf(CinemaReleaseJsonFilm("tmdb:1", mapOf("fr" to "Film"), "Film", "Film", "poster", "", 2026, "2026-01-10", 8.0, 100, 1.0)))))
        assertEquals("TMDB timeout", response.rows.first().error)
        assertEquals(1, response.rows[1].films.size)
    }

    @Test fun languageContractFallsBackToOriginal() {
        val film = film(1, "2026-01-11").copy(localizedTitles = mapOf("original" to "Original title"))
        val json = CinemaReleaseApiContract.fromRows("FR", "de", listOf(CinemaRow("Soon", listOf(film))))
        assertEquals("Original title", json.rows.single().films.single().displayTitle)
    }
}
