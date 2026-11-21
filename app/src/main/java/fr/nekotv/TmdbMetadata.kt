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
        val result = search.optJSONArray("results")?.let { selectTmdbResult(anime, it) } ?: return@withContext anime
        val id = result.optInt("id").takeIf { it > 0 } ?: return@withContext anime
        val details = getJson("/3/$kind/$id?language=fr-FR&append_to_response=external_ids,alternative_titles,translations", token)
        val dateKey = if (kind == "movie") "release_date" else "first_air_date"
        val firstAired = details.optString(dateKey).takeIf { it.isNotBlank() }
        val lastAired = details.optString("last_air_date").takeIf { it.isNotBlank() }
        val translated = details.optJSONObject("translations")?.optJSONArray("translations")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                val entry = array.optJSONObject(index) ?: return@mapNotNull null
                val code = entry.optString("iso_639_1")
                val value = entry.optJSONObject("data")?.optString(if (kind == "movie") "title" else "name").orEmpty()
                if (code in listOf("fr", "en", "ja") && value.isNotBlank()) code to value else null
            }.toMap()
        }.orEmpty()
        val localized = mapOf(
            "fr" to details.optString(if (kind == "movie") "title" else "name"),
            "original" to details.optString(if (kind == "movie") "original_title" else "original_name")
        ) + translated
        anime.copy(
            tmdbId = id,
            originalLanguage = details.optString("original_language").takeIf { it.matches(Regex("[a-z]{2}")) } ?: anime.originalLanguage,
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
            , localizedTitles = anime.localizedTitles.mergeTitles(localized)
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
    fun fold(value: String): String = java.text.Normalizer.normalize(value.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").replace(Regex("[^a-z0-9]+"), " ").trim()
    fun tokens(value: String): List<String> = fold(value).split(' ').filter(String::isNotBlank)
    val wanted = fold(query)
    val wantedTokens = tokens(query)
    val wantedKey = CatalogIdentity.title(query)

    fun titleFields(item: Anime): List<String> = buildList {
        add(item.title)
        addAll(item.aliases)
        addAll(item.localizedTitles.values)
        (listOf(item.title) + item.aliases + item.localizedTitles.values).forEach { value ->
            addAll(TitleAliases.variants(value))
        }
    }.filter(String::isNotBlank).distinct()

    fun popularityOf(item: Anime): Double = titleFields(item).flatMap { value ->
        listOf(CatalogIdentity.title(value), CatalogIdentity.identityTitle(value))
    }.mapNotNull { popularity[it] }.maxOrNull() ?: item.popularity

    fun relevance(item: Anime): Int {
        if (wanted.isBlank()) return 0
        val fields = titleFields(item)
        val foldedTitles = fields.map(::fold)
        val titleKeys = fields.flatMap { value ->
            listOf(CatalogIdentity.title(value), CatalogIdentity.identityTitle(value))
        }.toSet()
        val exact = foldedTitles.any { it == wanted } || wantedKey.isNotBlank() && wantedKey in titleKeys
        val allTitleTokens = foldedTitles.any { value -> wantedTokens.all { it in value.split(' ') } }
        val starts = foldedTitles.any { it.startsWith(wanted) }
        val contains = foldedTitles.any { it.contains(wanted) }
        val genreText = item.genres.joinToString(" ")
        val allGenreTokens = wantedTokens.isNotEmpty() && wantedTokens.all { it in tokens(genreText) ||
            tokens(genreText).any { genreToken -> genreToken.startsWith(it) } }
        val description = fold(item.description)
        val descriptionMatch = wantedTokens.isNotEmpty() && wantedTokens.all { it in description }
        return when {
            exact -> 700
            allTitleTokens -> 600
            starts -> 500
            contains -> 400
            allGenreTokens -> 300
            descriptionMatch -> 200
            else -> 0
        } + when {
            // Prefer an explicitly requested edition without making years part of the
            // identity used to merge translated/provider copies.
            CatalogIdentity.titleYear(query) != null && CatalogIdentity.effectiveYear(item) == CatalogIdentity.titleYear(query) -> 50
            else -> 0
        }
    }
    return items.sortedWith(compareByDescending<Anime> { relevance(it) }
        .thenByDescending { popularityOf(it) }
        .thenByDescending { it.references.map { r -> r.provider }.distinct().size })
}

/** Search-result gate: unrelated popular cards must never be shown for a non-empty query. */
internal fun isCatalogueMatch(item: Anime, query: String): Boolean {
    val fold: (String) -> String = { value -> java.text.Normalizer.normalize(value.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").replace(Regex("[^a-z0-9]+"), " ").trim() }
    val wanted = fold(query).split(' ').filter(String::isNotBlank)
    if (wanted.isEmpty()) return true
    val titles = (listOf(item.title) + item.aliases + item.localizedTitles.values +
        (listOf(item.title) + item.aliases + item.localizedTitles.values).flatMap(TitleAliases::variants)).map(fold)
    val titleTokens = titles.flatMap { it.split(' ') }.toSet()
    val titleMatch = titles.any { title -> wanted.all { it in title.split(' ') } || title.contains(fold(query).replace(" ", "")) }
    val genreTokens = fold(item.genres.joinToString(" ")).split(' ').filter(String::isNotBlank)
    val genreMatch = wanted.all { wantedToken -> genreTokens.any { it == wantedToken || it.startsWith(wantedToken) } }
    val description = fold(item.description)
    return titleMatch || genreMatch || wanted.all { it in description }
}

internal fun selectTmdbResult(anime: Anime, results: JSONArray): JSONObject? {
    val names = (anime.aliases + anime.title).map(CatalogIdentity::title).filter(String::isNotBlank).toSet()
    val movie = anime.tag == "Film"
    return (0 until results.length()).mapNotNull { results.optJSONObject(it) }.filter { candidate ->
        val year = candidate.optString(if (movie) "release_date" else "first_air_date").take(4).toIntOrNull()
        (anime.year == null || year == null || year == anime.year) &&
            listOf(if (movie) "title" else "name", if (movie) "original_title" else "original_name")
                .any { CatalogIdentity.title(candidate.optString(it)) in names }
    }.distinctBy { it.optInt("id") }.singleOrNull()
}
