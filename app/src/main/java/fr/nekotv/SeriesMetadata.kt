package fr.nekotv

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Strict identity matching: ambiguous names must not produce timestamps for another show. */
suspend fun enrichSeries(anime: Anime): Anime = withContext(Dispatchers.IO) {
    if (anime.tag != "Série") return@withContext anime
    fun get(path: String): String {
        val conn = URL("https://api.tvmaze.com/$path").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 4000; conn.readTimeout = 4000
            check(conn.responseCode == 200)
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally { conn.disconnect() }
    }
    try {
        val info = if (anime.imdbId?.matches(Regex("tt[0-9]{7,8}")) == true) JSONObject(get("lookup/shows?imdb=${anime.imdbId}"))
        else {
            val results = JSONArray(get("search/shows?q=${URLEncoder.encode(anime.title, "UTF-8")}"))
            (0 until results.length()).map { results.getJSONObject(it).getJSONObject("show") }.filter {
                CatalogIdentity.title(it.optString("name")) == CatalogIdentity.title(anime.title) &&
                    (anime.year == null || it.optString("premiered").take(4).toIntOrNull() == anime.year)
            }.singleOrNull() ?: return@withContext anime
        }
        fun text(key: String) = info.optString(key).takeUnless { it.isBlank() || it == "null" }
        val imdb = info.optJSONObject("externals")?.optString("imdb")?.takeIf { it.matches(Regex("tt[0-9]{7,8}")) }
        var episodes = anime.episodes
        // Anime providers sometimes use absolute numbering (S2 E25 instead of S2 E1).
        // Translate only when the canonical season can be uniquely identified.
        if (imdb != null) {
            val canonical = runCatching { JSONArray(get("shows/${info.getInt("id")}/episodes")) }.getOrNull()
            if (canonical != null) {
                val entries = (0 until canonical.length()).map { canonical.getJSONObject(it) }
                episodes = anime.episodes.map { ep ->
                    val season = entries.filter { it.optInt("season") == ep.seasonNumber }
                    val providerNumbers = anime.episodes.filter { it.seasonNumber == ep.seasonNumber }.map { it.number }.distinct().sorted()
                    val offset = entries.count { it.optInt("season") < ep.seasonNumber }
                    val number = if (providerNumbers.firstOrNull() == offset + 1 && offset > 0 && providerNumbers.size <= season.size) ep.number - offset else ep.number
                    val match = season.singleOrNull { it.optInt("number") == number }
                    ep.copy(introSeason = match?.optInt("season"), introNumber = match?.optInt("number"))
                }
            }
        }
        anime.copy(imdbId = anime.imdbId ?: imdb, firstAired = text("premiered"), lastAired = text("ended"),
            seriesStatus = when (text("status")) { "Running" -> "En cours"; "Ended" -> "Terminée"; "To Be Determined" -> "Suite à confirmer"; else -> null },
            metadataUrl = text("url"), episodes = episodes)
    } catch (e: CancellationException) { throw e }
    catch (_: Exception) { anime }
}
