package fr.nekotv

import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Optional TMDB enrichment. Missing or invalid credentials leave provider data unchanged. */
suspend fun enrichWithTmdb(anime: Anime): Anime = withContext(Dispatchers.IO) {
    val token = tmdbToken()
    if (token.isBlank() || anime.title.isBlank()) return@withContext anime
    try {
        val kind = if (anime.tag == "Film") "movie" else "tv"
        val search = getJson("/3/search/$kind?query=${Uri.encode(anime.title)}&language=fr-FR&include_adult=false", token)
        val result = search.optJSONArray("results")?.let { results ->
            (0 until results.length()).asSequence().map { results.getJSONObject(it) }.firstOrNull { candidate ->
                val key = if (kind == "movie") "release_date" else "first_air_date"
                val year = candidate.optString(key).take(4).toIntOrNull()
                anime.year == null || year == null || year == anime.year
            }
        } ?: return@withContext anime
        val id = result.optInt("id").takeIf { it > 0 } ?: return@withContext anime
        val details = getJson("/3/$kind/$id?language=fr-FR&append_to_response=external_ids,alternative_titles,translations", token)
        val dateKey = if (kind == "movie") "release_date" else "first_air_date"
        val firstAired = details.optString(dateKey).takeIf { it.isNotBlank() }
        val lastAired = details.optString("last_air_date").takeIf { it.isNotBlank() }
        anime.copy(
            poster = anime.poster.ifBlank { image(details.optString("poster_path")) },
            banner = anime.banner.ifBlank { image(details.optString("backdrop_path")) },
            description = anime.description.ifBlank { details.optString("overview") },
            year = anime.year ?: firstAired?.take(4)?.toIntOrNull(),
            genres = if (anime.genres.isEmpty()) details.optJSONArray("genres")?.orEmptyStrings().orEmpty() else anime.genres,
            imdbId = anime.imdbId ?: (details.optJSONObject("external_ids")?.optString("imdb_id") ?: details.optString("imdb_id")).takeIf { it.startsWith("tt") },
            firstAired = anime.firstAired ?: firstAired,
            lastAired = anime.lastAired ?: lastAired,
            seriesStatus = anime.seriesStatus ?: status(details.optString("status")),
            metadataUrl = anime.metadataUrl ?: "https://www.themoviedb.org/$kind/$id"
            ,aliases = (anime.aliases + listOf("title", "name", "original_title", "original_name").map { details.optString(it) } +
                details.optJSONObject("alternative_titles")?.let { root -> root.optJSONArray("titles") ?: root.optJSONArray("results") }?.let { array ->
                    (0 until array.length()).map { array.getJSONObject(it).optString("title") }
                }.orEmpty() + details.optJSONObject("translations")?.optJSONArray("translations")?.let { array ->
                    (0 until array.length()).map { array.getJSONObject(it) }.filter { it.optString("iso_639_1") in listOf("fr", "en", "ja") }
                        .mapNotNull { it.optJSONObject("data") }.map { it.optString(if (kind == "movie") "title" else "name") }
                }.orEmpty()).filter { it.isNotBlank() }.distinct()
        )
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        anime
    }
}

private fun getJson(path: String, token: String): JSONObject {
    val connection = (URL("https://api.themoviedb.org$path" + if (token.count { it == '.' } < 2) "&api_key=${Uri.encode(token)}" else "").openConnection() as HttpURLConnection).apply {
        connectTimeout = 5000
        readTimeout = 7000
        setRequestProperty("Accept", "application/json")
        if (token.count { it == '.' } >= 2) setRequestProperty("Authorization", "Bearer $token")
    }
    return try {
        check(connection.responseCode == HttpURLConnection.HTTP_OK)
        JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
    } finally {
        connection.disconnect()
    }
}

private fun JSONArray.orEmptyStrings(): List<String> = (0 until length()).mapNotNull { index ->
    optJSONObject(index)?.optString("name")?.takeIf { it.isNotBlank() }
}

private fun image(path: String): String = path.takeIf { it.startsWith("/") }?.let {
    "https://image.tmdb.org/t/p/w780$it"
}.orEmpty()

private fun status(value: String): String? = when (value) {
    "Returning Series" -> "En cours"
    "Ended", "Canceled" -> "Terminée"
    "Planned", "In Production", "Pilot" -> "À venir"
    else -> null
}
private fun tmdbToken(): String = BuildConfig.TMDB_READ_TOKEN.trim().ifBlank {
    runCatching { com.streamflixreborn.streamflix.utils.UserPreferences.tmdbApiKey.orEmpty().trim() }.getOrDefault("")
}

internal suspend fun cataloguePopularity(query: String): Map<String, Double> = withContext(Dispatchers.IO) {
    val token = tmdbToken()
    if (token.isBlank()) return@withContext emptyMap()
    try {
        val paths = if (query.isNotBlank()) listOf("/3/search/multi?query=${Uri.encode(query)}&language=fr-FR&include_adult=false")
            else listOf("/3/trending/all/week?language=fr-FR")
        val scores = mutableMapOf<String, Double>()
        for (path in paths) {
            val results = getJson(path, token).optJSONArray("results") ?: continue
            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                for (field in listOf("title", "name", "original_title", "original_name")) {
                    val title = CatalogIdentity.title(item.optString(field))
                    if (title.isNotBlank()) scores[title] = item.optDouble("popularity", 0.0)
                }
            }
        }
        scores
    } catch (e: CancellationException) { throw e }
    catch (_: Exception) { emptyMap() }
}

internal fun rankCatalogue(items: List<Anime>, query: String, popularity: Map<String, Double> = emptyMap()): List<Anime> {
    val wanted = CatalogIdentity.title(query)
    fun relevance(item: Anime): Int {
        val titles = (listOf(item.title) + item.aliases).map(CatalogIdentity::title)
        return when {
            wanted.isBlank() -> 0
            titles.any { it == wanted } -> 4
            titles.any { it.startsWith(wanted) } -> 3
            titles.any { it.contains(wanted) } -> 2
            else -> 0
        }
    }
    return items.sortedWith(compareByDescending<Anime> { relevance(it) }
        .thenByDescending { popularity[CatalogIdentity.title(it.title)] ?: 0.0 }
        .thenByDescending { it.references.map { r -> r.provider }.distinct().size })
}
