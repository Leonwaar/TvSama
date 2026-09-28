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
    val token = BuildConfig.TMDB_READ_TOKEN.trim()
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
        val details = getJson("/3/$kind/$id?language=fr-FR", token)
        val dateKey = if (kind == "movie") "release_date" else "first_air_date"
        val firstAired = details.optString(dateKey).takeIf { it.isNotBlank() }
        val lastAired = details.optString("last_air_date").takeIf { it.isNotBlank() }
        anime.copy(
            poster = anime.poster.ifBlank { image(details.optString("poster_path")) },
            banner = anime.banner.ifBlank { image(details.optString("backdrop_path")) },
            description = anime.description.ifBlank { details.optString("overview") },
            year = anime.year ?: firstAired?.take(4)?.toIntOrNull(),
            genres = if (anime.genres.isEmpty()) details.optJSONArray("genres")?.orEmptyStrings().orEmpty() else anime.genres,
            imdbId = anime.imdbId ?: details.optString("imdb_id").takeIf { it.startsWith("tt") },
            firstAired = anime.firstAired ?: firstAired,
            lastAired = anime.lastAired ?: lastAired,
            seriesStatus = anime.seriesStatus ?: status(details.optString("status")),
            metadataUrl = anime.metadataUrl ?: "https://www.themoviedb.org/$kind/$id"
        )
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        anime
    }
}

private fun getJson(path: String, token: String): JSONObject {
    val connection = (URL("https://api.themoviedb.org$path").openConnection() as HttpURLConnection).apply {
        connectTimeout = 5000
        readTimeout = 7000
        setRequestProperty("Accept", "application/json")
        setRequestProperty("Authorization", "Bearer $token")
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