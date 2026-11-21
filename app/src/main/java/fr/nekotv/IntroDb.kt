package fr.nekotv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private val introCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, EpisodeSegments>>()

data class IntroSegment(val start: Long, val end: Long)

data class SkipSegment(val label: String, val range: IntroSegment, val ending: Boolean = false)

data class EpisodeSegments(val intro: IntroSegment? = null, val outro: IntroSegment? = null,
    val additional: List<SkipSegment> = emptyList(), val postCredits: IntroSegment? = null) {
    val protectedUntil: Long get() = postCredits?.end ?: 0
}

suspend fun fetchIntro(imdb: String?, season: Int, episode: Int): IntroSegment? = fetchSegments(imdb, season, episode)?.intro

suspend fun fetchSegments(imdb: String?, season: Int, episode: Int, isMovie: Boolean = false): EpisodeSegments? = withContext(Dispatchers.IO) {
    if (imdb == null || !Regex("tt[0-9]{7,10}").matches(imdb) || (!isMovie && (season < 1 || episode < 1))) return@withContext null
    val key = "$imdb/$isMovie/$season/$episode"
    introCache[key]?.takeIf { System.currentTimeMillis() - it.first < 3_600_000 }?.let { return@withContext it.second }
    val query = if (isMovie) "imdb_id=$imdb&is_movie=true" else "imdb_id=$imdb&season=$season&episode=$episode"
    // Both providers answer in parallel: the skip button must be ready before the source
    // finishes negotiating, not after two sequential timeouts.
    val (data, backup) = coroutineScope {
        val primary = async { fetchIntroDb(query) }
        val fallback = async { fetchTheIntroDb(query) }
        primary.await() to fallback.await()
    }
    val primary = data?.let { parseSegments(it, imdb, season, episode, isMovie) }
    val needIntro = !isMovie && primary?.intro == null && data?.optBoolean("no_intro") != true
    val needOutro = primary?.outro == null && data?.optBoolean("no_outro") != true
    val intro = primary?.intro ?: backup?.intro?.takeIf { needIntro }
    val outro = primary?.outro ?: backup?.outro?.takeIf { needOutro }
    val extra = (primary?.additional.orEmpty() + backup?.additional.orEmpty() +
        listOfNotNull(backup?.outro?.takeIf { outro != null && it.start > outro.start + 15_000 }
            ?.let { SkipSegment("Passer le dernier générique", it, true) }))
        .filterNot { part -> listOfNotNull(intro, outro).any { kotlin.math.abs(it.start - part.range.start) <= 5_000 } }
        .distinctBy { "${it.label}:${it.range.start / 5_000}" }.sortedBy { it.range.start }
    EpisodeSegments(intro, outro, extra, primary?.postCredits ?: backup?.postCredits)
        .also { if (it.intro != null || it.outro != null || it.additional.isNotEmpty() || it.protectedUntil > 0 ||
            data?.optBoolean("no_intro") == true && data.optBoolean("no_outro") == true)
            introCache[key] = System.currentTimeMillis() to it }
}

private fun fetchIntroDb(query: String): JSONObject? {
    val connection = URL("https://api.introdb.app/segments?$query").openConnection() as HttpURLConnection
    return try {
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        if (connection.responseCode == 200) JSONObject(connection.inputStream.bufferedReader().use { it.readText() }) else null
    } catch (_: java.io.IOException) { null
    } catch (_: org.json.JSONException) { null
    } finally { connection.disconnect() }
}

private fun fetchTheIntroDb(query: String): EpisodeSegments? {
    val connection = URL("https://api.theintrodb.org/v3/media?$query").openConnection() as HttpURLConnection
    return try {
        connection.connectTimeout = 3_000; connection.readTimeout = 3_000
        if (connection.responseCode != 200) null
        else parseTheIntroDb(JSONObject(connection.inputStream.bufferedReader().use { it.readText() }))
    } catch (_: java.io.IOException) { null
    } catch (_: org.json.JSONException) { null
    } finally { connection.disconnect() }
}

internal fun parseTheIntroDb(data: JSONObject): EpisodeSegments {
    fun ranges(name: String, ending: Boolean): List<IntroSegment> {
        val values = data.optJSONArray(name) ?: return emptyList()
        return (0 until values.length()).mapNotNull { index ->
            val value = values.optJSONObject(index) ?: return@mapNotNull null
            val start = if (value.isNull("start_ms")) 0L else value.optLong("start_ms", -1)
            val end = if (ending && value.isNull("end_ms")) Long.MAX_VALUE else value.optLong("end_ms", -1)
            if (start >= 0 && end > start) IntroSegment(start, end) else null
        }.sortedBy { it.start }
    }
    val intros = ranges("intro", false)
    val credits = ranges("credits", true)
    val additional = intros.drop(1).map { SkipSegment("Passer l’intro", it) } +
        ranges("recap", false).map { SkipSegment("Passer le récapitulatif", it) } +
        credits.dropLast(1).map { SkipSegment("Passer le générique", it, true) } +
        ranges("preview", true).map { SkipSegment("Passer l’aperçu", it, true) }
    return EpisodeSegments(intros.firstOrNull(), credits.lastOrNull(), additional)
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
    val recap = data.optJSONObject("recap")?.let(::parseSegment)
    val postCredits = data.optJSONObject("post_credits")?.let(::parseSegment)
    return EpisodeSegments(data.optJSONObject("intro")?.let(::parseSegment), data.optJSONObject("outro")?.let(::parseSegment),
        listOfNotNull(recap?.let { SkipSegment("Passer le récapitulatif", it) }), postCredits)
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
