package fr.nekotv

import java.time.LocalDate

/** Pure release-row policy, kept separate from network/cache code for deterministic tests. */
internal object CinemaReleaseRanking {
    enum class Row { WEEK, NOW, UPCOMING }

    fun filterUsable(films: List<Anime>): List<Anime> = films.filter {
        it.tag == "Film" && it.title.isNotBlank() && it.poster.isNotBlank() &&
            !it.firstAired.isNullOrBlank() && !it.firstAired.equals("null", true)
    }.filterNot { it.provider == "adult" || it.genres.any { genre -> genre.equals("adult", true) } }

    fun partition(films: List<Anime>, today: LocalDate = LocalDate.now(), limit: Int = 20): Map<Row, List<Anime>> {
        val valid = filterUsable(films).distinctBy { it.tmdbId?.let { id -> "tmdb:$id" } ?: it.imdbId ?: it.id }
        val week = valid.filter { parse(it) in today..today.plusDays(7) }
        val now = valid.filter { parse(it) < today }
        val upcoming = valid.filter { parse(it) > today.plusDays(7) }
        val used = mutableSetOf<String>()
        fun key(film: Anime) = film.tmdbId?.let { "tmdb:$it" } ?: film.imdbId ?: film.id
        fun takeUnique(source: List<Anime>): List<Anime> = source.filter { used.add(key(it)) }.take(limit)
        return linkedMapOf(
            Row.WEEK to takeUnique(week.sortedWith(compareBy<Anime> { it.firstAired }.thenByDescending { it.popularity }).also { }),
            Row.NOW to takeUnique(now.sortedWith(compareByDescending<Anime> { score(it) }.thenBy { key(it) })),
            Row.UPCOMING to takeUnique(upcoming.sortedWith(compareBy<Anime> { it.firstAired }.thenByDescending { it.popularity }))
        )
    }

    private fun parse(film: Anime): LocalDate = runCatching { LocalDate.parse(film.firstAired) }.getOrElse { LocalDate.MAX }
    private fun score(film: Anime): Double = kotlin.math.ln(1.0 + film.popularity.coerceAtLeast(0.0)) *
        (film.rating.coerceIn(0.0, 10.0) / 10.0) * kotlin.math.ln(1.0 + film.voteCount.coerceAtLeast(0))
}
