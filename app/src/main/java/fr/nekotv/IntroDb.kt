package fr.nekotv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private val introCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, EpisodeSegments>>()

data class IntroSegment(val start: Long, val end: Long)

data class EpisodeSegments(val intro: IntroSegment? = null, val outro: IntroSegment? = null)

suspend fun fetchIntro(imdb: String?, season: Int, episode: Int): IntroSegment? = fetchSegments(imdb, season, episode)?.intro

suspend fun fetchSegments(imdb: String?, season: Int, episode: Int, isMovie: Boolean = false): EpisodeSegments? = withContext(Dispatchers.IO) {
    if (imdb == null || !Regex("tt[0-9]{7,10}").matches(imdb) || (!isMovie && (season < 1 || episode < 1))) return@withContext null
    val key = "$imdb/$isMovie/$season/$episode"
    introCache[key]?.takeIf { System.currentTimeMillis() - it.first < 3_600_000 }?.let { return@withContext it.second }
    val query = if (isMovie) "imdb_id=$imdb&is_movie=true" else "imdb_id=$imdb&season=$season&episode=$episode"
    val connection = URL("https://api.introdb.app/segments?$query").openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        if (connection.responseCode != 200) return@withContext null
        val data = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        parseSegments(data, imdb, season, episode, isMovie)?.also { introCache[key] = System.currentTimeMillis() to it }
    } catch (_: java.io.IOException) { null
    } catch (_: org.json.JSONException) { null
    } finally { connection.disconnect() }
}

internal fun parseIntro(data: JSONObject, imdb: String, season: Int, episode: Int): IntroSegment? {
    if (data.optString("imdb_id") != imdb || data.optInt("season") != season || data.optInt("episode") != episode) return null
    return parseSegment(data)
}

internal fun parseSegments(data: JSONObject, imdb: String, season: Int, episode: Int, isMovie: Boolean = false): EpisodeSegments? {
    if (data.optString("imdb_id") != imdb) return null
    if (isMovie) {
        if (data.optString("media_type") != "movie" && !data.optBoolean("is_movie")) return null
        return EpisodeSegments(outro = data.optJSONObject("outro")?.let(::parseSegment))
    }
    if (data.optInt("season") != season || data.optInt("episode") != episode || data.optString("media_type", "tv") != "tv") return null
    return EpisodeSegments(data.optJSONObject("intro")?.let(::parseSegment), data.optJSONObject("outro")?.let(::parseSegment))
}

private fun parseSegment(data: JSONObject): IntroSegment? {
    fun millis(field: String): Long? {
        val value = if (data.has("${field}_ms") && !data.isNull("${field}_ms")) data.optDouble("${field}_ms", Double.NaN)
            else data.optDouble("${field}_sec", Double.NaN) * 1000
        return value.takeIf { it.isFinite() && it >= 0 && it < Long.MAX_VALUE.toDouble() }?.toLong()
    }
    val start = millis("start")
    val end = millis("end")
    return if (start != null && end != null && end > start) IntroSegment(start, end) else null
}
