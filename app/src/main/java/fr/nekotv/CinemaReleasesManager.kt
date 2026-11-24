package fr.nekotv

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Configurable, stale-while-revalidate cache for the independent cinema rows. */
class CinemaReleasesManager(context: Context) {
    companion object {
        // Home release configuration: keep all product decisions in one place.
        const val DEFAULT_REGION = "FR"
        const val DEFAULT_LANGUAGE = "fr"
        const val MAX_PER_ROW = 20
        const val TTL_MS = 3 * 60 * 60_000L
        const val HEADER_BANNER_ENABLED = true
    }

    private val prefs = context.getSharedPreferences("cinema_release_rows", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val type = object : TypeToken<Map<String, List<Anime>>>() {}.type

    fun cached(): List<CinemaRow> = runCatching {
        decode(prefs.getString("rows", "{}"))
    }.getOrDefault(emptyMap()).toRows()

    fun isFresh(): Boolean = System.currentTimeMillis() - prefs.getLong("checked", 0) < TTL_MS

    suspend fun load(region: String = DEFAULT_REGION, language: String = DEFAULT_LANGUAGE): List<CinemaRow> = withContext(Dispatchers.IO) {
        var fetched = tmdbReleaseRows(region, language)
        
        if (fetched.isEmpty()) {
            val allocineFilms = runCatching { AlloCineReleases.load() }.getOrElse { emptyList() }
            if (allocineFilms.isNotEmpty()) {
                val rows = CinemaReleaseRanking.partition(allocineFilms, java.time.LocalDate.now(), MAX_PER_ROW)
                fetched = mapOf(
                    "week" to rows[CinemaReleaseRanking.Row.WEEK].orEmpty(),
                    "now" to rows[CinemaReleaseRanking.Row.NOW].orEmpty(),
                    "upcoming" to rows[CinemaReleaseRanking.Row.UPCOMING].orEmpty()
                )
            }
        }
        if (fetched.isNotEmpty()) {
            prefs.edit().putString("rows", gson.toJson(fetched, type)).putLong("checked", System.currentTimeMillis()).apply()
            fetched.toRows()
        } else cached()
    }

    private fun decode(raw: String?): Map<String, List<Anime>> =
        gson.fromJson<Map<String, List<Anime>>>(raw ?: "{}", type) ?: emptyMap()

    private fun Map<String, List<Anime>>.toRows(): List<CinemaRow> = listOf(
        CinemaRow("Cette semaine", this["week"].orEmpty()),
        CinemaRow("À l'affiche", this["now"].orEmpty()),
        CinemaRow("Prochainement", this["upcoming"].orEmpty())
    )
}

data class CinemaRow(val title: String, val films: List<Anime>)
