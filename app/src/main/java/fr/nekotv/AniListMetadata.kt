package fr.nekotv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

internal data class AniListTitle(val id: Int, val titles: List<String>, val synonyms: List<String>,
    val year: Int?, val episodes: Int?, val country: String?)

private data class CachedAniList(val expiresAt: Long, val title: AniListTitle?)
private val aniListCache = ConcurrentHashMap<String, CachedAniList>()

internal fun selectAniListTitle(anime: Anime, candidates: List<AniListTitle>): AniListTitle? {
    val names = (anime.aliases + anime.title).map(CatalogIdentity::title).filter(String::isNotBlank).toSet()
    val exact = candidates.filter { candidate ->
        (candidate.titles + candidate.synonyms).map(CatalogIdentity::title).any { it in names }
    }
    // A year written in the title ("Hunter x Hunter 2011") separates two editions even
    // when the provider did not publish a release year.
    val year = CatalogIdentity.effectiveYear(anime)
    val yearMatches = exact.filter { it.year == year }
    return (yearMatches.ifEmpty { exact }).singleOrNull()
}

internal fun vidriftAniListEpisode(anime: Anime, episode: Episode): Int? {
    val ordered = anime.episodes.distinctBy { it.seasonNumber to it.number }
        .sortedWith(compareBy<Episode> { it.seasonNumber }.thenBy { it.number })
    return (ordered.indexOfFirst { it.id == episode.id }.takeIf { it >= 0 } ?: run {
        ordered.indexOfFirst { it.seasonNumber == episode.seasonNumber && it.number == episode.number }.takeIf { it >= 0 }
    })?.plus(1)
}

internal suspend fun findAniListTitle(anime: Anime): AniListTitle? = withContext(Dispatchers.IO) {
    val key = CatalogIdentity.title(anime.title) + "|" + (CatalogIdentity.effectiveYear(anime) ?: "")
    aniListCache[key]?.takeIf { it.expiresAt > System.currentTimeMillis() }?.let { return@withContext it.title }
    val connection = (URL("https://graphql.anilist.co").openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 3_500
        readTimeout = 4_500
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", "TvSama/1.0")
    }
    val query = "query (\$search: String) { Page(page: 1, perPage: 10) { media(search: \$search, type: ANIME, isAdult: false) { id title { romaji english native userPreferred } synonyms startDate { year } episodes countryOfOrigin } } }"
    val request = JSONObject().put("query", query).put("variables", JSONObject().put("search", anime.title)).toString()
    val result = try {
        connection.outputStream.use { it.write(request.toByteArray(Charsets.UTF_8)) }
        if (connection.responseCode !in 200..299) null else {
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val media = JSONObject(body).optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("media")
            List(media?.length() ?: 0) { index ->
                val item = media!!.optJSONObject(index) ?: JSONObject()
                val title = item.optJSONObject("title") ?: JSONObject()
                AniListTitle(item.optInt("id"), listOf("romaji", "english", "native", "userPreferred")
                    .mapNotNull { title.optString(it).takeIf(String::isNotBlank) },
                    item.optJSONArray("synonyms")?.let { array -> List(array.length()) { array.optString(it) } }.orEmpty(),
                    item.optJSONObject("startDate")?.optInt("year")?.takeIf { it > 0 },
                    item.optInt("episodes").takeIf { it > 0 }, item.optString("countryOfOrigin").takeIf(String::isNotBlank))
            }
        }
    } catch (_: Exception) { null } finally { connection.disconnect() }
    val selected = result?.let { selectAniListTitle(anime, it) }
    aniListCache[key] = CachedAniList(System.currentTimeMillis() + if (selected != null) 5 * 24 * 60 * 60_000L else 60 * 60_000L, selected)
    selected
}

internal fun languageForAniListCountry(country: String?): String? = when (country) {
    "JP" -> "ja"
    "KR" -> "ko"
    "CN" -> "zh"
    "FR" -> "fr"
    else -> null
}
