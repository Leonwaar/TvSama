package fr.nekotv

import com.google.gson.Gson

/** Stable JSON contract for a future HTTP facade (and for local cache/debug fixtures). */
data class CinemaReleaseResponse(
    val region: String,
    val language: String,
    val rows: List<CinemaReleaseJsonRow>
)

data class CinemaReleaseJsonRow(
    val id: String,
    val title: String,
    val films: List<CinemaReleaseJsonFilm>,
    val error: String? = null
)

data class CinemaReleaseJsonFilm(
    val canonicalId: String,
    val titles: Map<String, String>,
    val originalTitle: String,
    val displayTitle: String,
    val poster: String,
    val backdrop: String,
    val year: Int?,
    val releaseDate: String,
    val rating: Double,
    val votes: Int,
    val popularity: Double
)

internal object CinemaReleaseApiContract {
    fun fromRows(region: String, language: String, rows: List<CinemaRow>): CinemaReleaseResponse =
        CinemaReleaseResponse(region, language, rows.mapIndexed { index, row ->
            CinemaReleaseJsonRow(index.toString(), row.title, row.films.map { film ->
                CinemaReleaseJsonFilm(
                    canonicalId = film.tmdbId?.let { "tmdb:$it" } ?: film.imdbId ?: film.id,
                    titles = film.localizedTitles,
                    originalTitle = film.localizedTitles["original"] ?: film.title,
                    displayTitle = film.displayTitle(language),
                    poster = film.poster,
                    backdrop = film.banner,
                    year = film.year,
                    releaseDate = film.firstAired.orEmpty(),
                    rating = film.rating,
                    votes = film.voteCount,
                    popularity = film.popularity
                )
            })
        })

    fun toJson(response: CinemaReleaseResponse): String = Gson().toJson(response)
}
