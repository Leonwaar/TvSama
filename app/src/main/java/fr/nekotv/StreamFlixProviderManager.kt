package fr.nekotv

import android.content.Context
import com.streamflixreborn.streamflix.StreamFlixApp
import com.streamflixreborn.streamflix.providers.Provider
import com.streamflixreborn.streamflix.providers.ProviderConfigUrl
import com.streamflixreborn.streamflix.providers.IptvProvider
import com.streamflixreborn.streamflix.models.Movie
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.models.Video
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.streamflixreborn.streamflix.utils.UserPreferences
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.text.Normalizer
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import java.net.HttpURLConnection
import java.net.URL

/** TvSama owns its catalogue; only extraction adapters are shared with StreamFlix. */
class StreamFlixProviderManager private constructor() {
    private val externalSources = listOf(
        ExternalSource("Neko-Sama", "https://animes-sama.su/"),
        ExternalSource("FRAnime", "https://franime.fr/"),
        ExternalSource("Anime-Sama", "https://anime-sama.to/catalogue/")
    )
    private val providers by lazy { Provider.providers.keys.filter { it.language == "fr" } }
    private val preferences by lazy { StreamFlixApp.instance.getSharedPreferences("tvsama_settings", Context.MODE_PRIVATE) }
    private val statuses = ConcurrentHashMap<String, String>()
    private val details = ConcurrentHashMap<String, Anime>()
    private val streamCache = ConcurrentHashMap<String, Pair<Long, List<VideoSource>>>()
    private val requests = Semaphore(4)
    private val extractors = Semaphore(4)
    private val extractionContext = Mutex()
    private var currentName: String? = null

    companion object {
        private val singleton by lazy { StreamFlixProviderManager() }
        fun getInstance() = singleton
    }
    suspend fun getProviders() = providers.toList()
    suspend fun getProviderNames() = providers.map { it.name } + externalSources.map { it.name }
    fun externalSource(name: String) = externalSources.firstOrNull { it.name == name }
    fun isProviderEnabled(name: String) = name !in preferences.getStringSet("disabled_providers", emptySet()).orEmpty()
    @Synchronized
    fun setProviderEnabled(name: String, enabled: Boolean) {
        val disabled = preferences.getStringSet("disabled_providers", emptySet()).orEmpty().toMutableSet()
        if (enabled) disabled.remove(name) else disabled.add(name)
        preferences.edit().putStringSet("disabled_providers", disabled).apply()
        streamCache.clear()
    }
    fun providerStatuses(): Map<String, String> = statuses.toMap()
    suspend fun setCurrentProviderByName(name: String): Boolean {
        if (providers.none { it.name == name }) return false
        currentName = name
        return true
    }
    suspend fun getCurrentProvider() = providers.firstOrNull { it.name == currentName } ?: providers.first()
    private fun owner(name: String) = providers.firstOrNull { it.name == name }
        ?: error("Source introuvable : $name")
    private fun identity(a: Anime) = "${a.provider}|${a.tag}|${a.id}"
    private fun normalized(value: String) = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").replace(languageMarker, " ")
        .replace(Regex("[^a-z0-9]"), "")

    private val languageMarker = Regex(
        "(?i)(?<![a-z])(truefrench|french|vfq|vff|vostfr|vost|vf|vo|fr)(?![a-z])"
    )

    private fun displayTitle(value: String) = value
        .replace(languageMarker, " ")
        .replace(Regex("\\s{2,}"), " ")
        .trim(' ', '-', '·', '|')

    private fun languageHint(value: String): String? {
        val marker = languageMarker.find(value)?.value?.lowercase() ?: return null
        return if (marker == "vo" || marker.startsWith("vost")) "VOSTFR" else "VF"
    }

    private fun searchQueries(query: String): List<String> {
        val trimmed = query.trim()
        val folded = Normalizer.normalize(trimmed.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return listOf(trimmed, folded, folded.replace(" ", ""))
            .filter { it.isNotBlank() }
            .distinct()
    }

    private suspend fun <T> attempt(name: String, fallback: T, timeoutMillis: Long = 25_000, block: suspend () -> T): T = try {
        withTimeout(timeoutMillis) { block() }.also { statuses[name] = "Disponible" }
    } catch (e: TimeoutCancellationException) {
        currentCoroutineContext().ensureActive()
        statuses[name] = "Délai dépassé"; fallback
    } catch (e: CancellationException) { throw e
    } catch (e: Exception) {
        statuses[name] = "Indisponible • ${e.message?.take(70) ?: "erreur réseau"}"; fallback
    }

    suspend fun searchAllProviders(query: String, category: String, page: Int = 1): List<Anime> = withContext(Dispatchers.IO) {
        val cat = category.lowercase()
        val selected = providers.filter { isProviderEnabled(it.name) }.filter {
            when {
                cat.contains("direct") || cat.contains("tv en") -> it is IptvProvider
                cat.contains("anime") || cat.contains("animé") || cat.contains("animation") -> it.name.contains("anime", true) || it.name.contains("manga", true)
                else -> it !is IptvProvider
            }
        }
        val found = selected.map { provider -> async {
            requests.withPermit { attempt(provider.name, emptyList<Anime>()) {
                val items = when {
                    query.isNotBlank() -> {
                        val variants = searchQueries(query)
                        var result = provider.search(variants.first(), page)
                        for (variant in variants.drop(1)) {
                            if (result.isNotEmpty()) break
                            result = provider.search(variant, page)
                        }
                        result
                    }
                    cat.contains("film") -> if (Provider.supportsMovies(provider)) provider.getMovies(page) else emptyList()
                    cat.contains("série") || cat.contains("serie") -> if (Provider.supportsTvShows(provider)) provider.getTvShows(page) else emptyList()
                    page > 1 -> if (Provider.supportsTvShows(provider)) provider.getTvShows(page) else provider.getMovies(page)
                    else -> provider.getHome().flatMap { it.list }
                }
                items.mapNotNull { item -> when(item) {
                    is Movie -> movie(item, provider.name)
                    is TvShow -> show(item, provider.name)
                    is com.streamflixreborn.streamflix.models.Episode -> item.tvShow?.let { show(it, provider.name) }
                    else -> null
                } }.filter { a -> a.id !in setOf("creador-info", "apoyo-nando") }.filter { a ->
                    when { cat.contains("film") -> a.tag == "Film"
                        cat.contains("série") || cat.contains("serie") -> a.tag != "Film"
                        else -> true }
                }.distinctBy { identity(it) }
            } }
                } }.awaitAll().flatten()
        val requestedLanguage = when {
            cat.contains("vost") || cat.contains("vo sous") -> "VOSTFR"
            cat == "vf" || cat.contains("version française") -> "VF"
            else -> null
        }
        val languageFiltered = found.filter { item ->
            requestedLanguage == null || languageHint(item.title) == requestedLanguage
        }
        // Keep ambiguous years separate to avoid merging remakes.
        languageFiltered.groupBy { "${normalized(it.title)}|${it.year}|${it.tag}" }.values.map { group ->
            group.first().copy(
                title = displayTitle(group.first().title),
                references = group.flatMap { it.references }.distinct()
            )
        }
    }

    suspend fun loadDetails(anime: Anime): Anime = withContext(Dispatchers.IO) {
        val references = (listOf(MediaReference(anime.provider, anime.id, anime.tag)) + anime.references)
            .distinct().filter { isProviderEnabled(it.provider) }
        var lastFailure: Exception? = null
        for (ref in references) {
            val candidate = anime.copy(provider = ref.provider, id = ref.id, tag = ref.tag)
            try {
                val result = loadProviderDetails(candidate).copy(references = anime.references.ifEmpty { references })
                if (result.episodes.isEmpty()) {
                    statuses[ref.provider] = "Aucun épisode disponible"
                    continue
                }
                return@withContext result
            } catch (e: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                statuses[ref.provider] = "Délai dépassé"
                lastFailure = e
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                statuses[ref.provider] = "Fiche indisponible • ${e.message?.take(60).orEmpty()}"
                lastFailure = e
            }
        }
        throw IllegalStateException("Aucune fiche disponible sur les sources activées. Réessayez ou actualisez les sources.", lastFailure)
    }

    private suspend fun loadProviderDetails(anime: Anime): Anime {
        details[identity(anime)]?.let { return it.copy(references = anime.references) }
        val provider = owner(anime.provider)
        var partial = false
        val loaded = if (anime.tag == "Film") withTimeout(35_000) { movie(provider.getMovie(anime.id), provider.name) }
        else {
            val tv = withTimeout(35_000) { provider.getTvShow(anime.id) }
            val seasonResults = coroutineScope { tv.seasons.map { season -> async {
                requests.withPermit {
                    try {
                        val eps = if (season.episodes.isNotEmpty()) season.episodes else
                            withTimeout(25_000) { provider.getEpisodesBySeason(season.id) }
                        eps.map { ep ->
                            val title = ep.title ?: "Épisode ${ep.number}"
                            Episode(title, ep.overview.orEmpty(), emptyList(), ep.id, ep.number, season.number, ep.poster.orEmpty(), episodeLanguage(title, ep.overview.orEmpty(), provider.language))
                        } to false
                    } catch (e: TimeoutCancellationException) {
                        currentCoroutineContext().ensureActive()
                        emptyList<Episode>() to true
                    } catch (e: CancellationException) { throw e
                    } catch (e: Exception) { emptyList<Episode>() to true }
                }
            } }.awaitAll() }
            partial = seasonResults.any { it.second }
            val episodes = seasonResults.flatMap { it.first }
                .distinctBy { "${it.seasonNumber}|${it.id}" }
                .sortedWith(compareBy<Episode> { it.seasonNumber }.thenBy { it.number })
            show(tv, provider.name).copy(episodes = episodes)
        }
        statuses[provider.name] = if (partial) "Disponible • certaines saisons n’ont pas répondu" else "Disponible"
        val result = loaded.copy(references = anime.references,
            poster = loaded.poster.ifBlank { anime.poster }, banner = loaded.banner.ifBlank { anime.banner })
        // A partial response remains retryable instead of poisoning the detail cache.
        if (!partial && result.episodes.isNotEmpty()) details[identity(anime)] = result
        return result
    }
    suspend fun getDetails(anime: Anime) = loadDetails(anime)

    suspend fun refreshSources() = withContext(Dispatchers.IO) {
        details.clear(); streamCache.clear()
        val providerRefreshes = providers.filter { isProviderEnabled(it.name) }.map { p -> async {
            if (p is ProviderConfigUrl) requests.withPermit { attempt(p.name, Unit) { p.onChangeUrl(true); Unit } }
        } }
        val externalRefreshes = externalSources.filter { isProviderEnabled(it.name) }.map { source -> async {
            requests.withPermit { checkExternalSource(source) }
        } }
        (providerRefreshes + externalRefreshes).awaitAll()
        Unit
    }

    private suspend fun checkExternalSource(source: ExternalSource) = attempt(source.name, Unit) {
        val connection = (URL(source.url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "TvSama/1.0 AndroidTV")
            connect()
        }
        try {
            if (connection.responseCode !in 200..399) error("HTTP ${connection.responseCode}")
        } finally { connection.disconnect() }
    }

    suspend fun resolveSources(anime: Anime, episode: Episode? = null, language: String = "Toutes", refresh: Boolean = false): List<VideoSource> = withContext(Dispatchers.IO) {
        if (anime.episodes.isEmpty()) return@withContext resolveSources(loadDetails(anime), episode, language, refresh)
        val selected = episode ?: anime.episodes.firstOrNull() ?: return@withContext emptyList()
        val cacheKey = "${identity(anime)}|${selected.seasonNumber}|${selected.number}|${selected.id}|$language"
        if (!refresh) streamCache[cacheKey]?.takeIf { System.currentTimeMillis() - it.first < 120_000 }?.let { return@withContext it.second }
        val refs = anime.references.ifEmpty { listOf(MediaReference(anime.provider, anime.id, anime.tag)) }
        val sources = refs.filter { isProviderEnabled(it.provider) }.map { ref -> async {
            extractionContext.withLock { attempt(ref.provider, emptyList<VideoSource>(), 120_000) {
                val p = owner(ref.provider)
                if (UserPreferences.currentProvider?.name != p.name) UserPreferences.currentProvider = p
                val matching = if (ref.provider == anime.provider && ref.id == anime.id) anime else
                    loadDetails(anime.copy(id = ref.id, provider = ref.provider, tag = ref.tag, episodes = emptyList(), references = listOf(ref)))
                val ep = if (ref.provider == anime.provider && ref.id == anime.id) selected else
                    matching.episodes.firstOrNull { it.number == selected.number && it.seasonNumber == selected.seasonNumber }
                        ?: return@attempt emptyList()
                val type: Video.Type = if (matching.tag == "Film") Video.Type.Movie(matching.id, matching.title, matching.year?.toString().orEmpty(), matching.poster, matching.imdbId)
                    else Video.Type.Episode(ep.id, ep.number, ep.title, ep.poster, ep.description,
                        Video.Type.Episode.TvShow(matching.id, matching.title, matching.poster, matching.banner, matching.year?.toString(), matching.imdbId),
                        Video.Type.Episode.Season(ep.seasonNumber, "Saison ${ep.seasonNumber}"))
                val servers = p.getServers(if (matching.tag == "Film") matching.id else ep.id, type)
                coroutineScope { servers.map { server -> async {
                    extractors.withPermit {
                        try {
                            withTimeout(18_000) {
                                val label = "${server.name} ${server.id} ${matching.title} ${matching.id}"
                                if (Regex("(?i)\\bvo\\b").containsMatchIn(server.name)) return@withTimeout null
                                val video = p.getVideo(server)
                        val lang = detectLanguage(label, video, p)
                        if (lang !in listOf("VF", "VOSTFR") || (language != "Toutes" && lang != language)) return@withTimeout null
                                if (!video.source.startsWith("http")) return@withTimeout null
                                val raw = VideoSource(server.name.ifBlank { p.name }, video.source, lang, quality(server.name + " " + video.source), p.name,
                                    video.headers.orEmpty(), video.subtitles.filter { french(it.label) }.map {
                                        VideoSubtitle(it.file, "fr", it.label, if (it.file.substringBefore('?').endsWith(".srt")) "application/x-subrip" else "text/vtt")
                                    }, video.type, serverId = server.id)
                                probe(raw)
                            }
                        } catch (e: TimeoutCancellationException) { currentCoroutineContext().ensureActive(); null }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) { null }
                    }
                } }.awaitAll().filterNotNull() }
            } }
        } }.awaitAll().flatten().distinctBy { "${it.url}|${it.headers}|${it.language}" }
            .sortedWith(compareByDescending<VideoSource> { qualityScore(it.quality) }.thenBy { it.headers.isNotEmpty() }.thenBy { it.provider })
        if (sources.isEmpty() && !refresh) {
            // Providers such as Frembed/Kidraz publish rotating domains from GitHub.
            // Refresh those adapters once, then resolve every provider again.
            refreshSources()
            return@withContext resolveSources(anime, selected, language, refresh = true)
        }
        streamCache[cacheKey] = System.currentTimeMillis() to sources
        sources.sortedWith(compareByDescending<VideoSource> { qualityScore(it.quality) }
            .thenBy { if (it.latencyMs < 0) Long.MAX_VALUE else it.latencyMs }
            .thenBy { it.provider })
    }
    suspend fun getStreamsForEpisode(anime: Anime, episode: Episode, language: String = "Toutes") = resolveSources(anime, episode, language)
    suspend fun getStreamsForAnime(anime: Anime) = resolveSources(anime)
    suspend fun getAllStreamsForAnime(anime: Anime) = resolveSources(anime)
    private fun french(label: String) = Regex("(?i)french|français|francais|\\bfr\\b|vostfr").containsMatchIn(label)
    private fun episodeLanguage(title: String, overview: String, providerLanguage: String): String {
        val value = "$title $overview"
        return when {
            Regex("(?i)vostfr|sous[- ]?titres?|sub(fr|title)").containsMatchIn(value) -> "VOSTFR"
            Regex("(?i)\\b(vf|vff|vfq|français|french|doublage)\\b").containsMatchIn(value) -> "VF"
            else -> "UNKNOWN"
        }
    }
    private fun detectLanguage(label: String, video: Video, provider: Provider): String = when {
        Regex("(?i)\\b(vostfr|vost|vo|original|japanese|english)\\b").containsMatchIn(label) -> "VOSTFR"
        Regex("(?i)\\b(vf|vff|vfq|truefrench|french|fr)\\b").containsMatchIn(label) -> "VF"
        video.subtitles.any { french(it.label) } &&
            !Regex("(?i)\\b(vf|vff|vfq|french|fr)\\b").containsMatchIn(label) -> "VOSTFR"
        else -> "UNKNOWN"
    }
    private fun quality(label: String) = when {
        Regex("(?i)2160|4k|uhd").containsMatchIn(label) -> "4K"
        Regex("(?i)1080|fhd").containsMatchIn(label) -> "1080p"
        label.contains("720") -> "720p"
        label.contains("480") -> "480p"
        else -> "Auto"
    }
    private fun qualityScore(q: String) = when(q) { "4K" -> 4; "1080p" -> 3; "720p" -> 2; "480p" -> 1; else -> 0 }
    private suspend fun probe(source: VideoSource): VideoSource = withContext(Dispatchers.IO) {
        val started = System.nanoTime()
        val connection = runCatching {
            (URL(source.url).openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                connectTimeout = 2_500
                readTimeout = 3_500
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "TvSama/1.0 AndroidTV")
                source.headers.forEach { (key, value) -> setRequestProperty(key, value) }
                connect()
            }
        }.getOrNull() ?: return@withContext source.copy(latencyMs = -1, reachable = false)
        return@withContext try {
            val code = connection.responseCode
            val latency = ((System.nanoTime() - started) / 1_000_000L).coerceAtLeast(1L)
            // A 405 means the host is alive but does not implement HEAD; keep it as an unknown-latency candidate.
            source.copy(latencyMs = latency, reachable = code in 200..399 || code == 405)
        } catch (_: Exception) {
            source.copy(latencyMs = -1, reachable = false)
        } finally { connection.disconnect() }
    }
    private fun movie(m: Movie, provider: String) = Anime(m.title, "Film", listOf(Episode("Film", m.overview.orEmpty(), emptyList(), m.id)),
        m.id, m.poster.orEmpty(), m.overview.orEmpty(), m.released?.get(Calendar.YEAR), m.genres.map { it.name },
        provider, m.banner.orEmpty(), listOf(MediaReference(provider, m.id, "Film")), m.imdbId)
    private fun show(t: TvShow, provider: String) = Anime(t.title, "Série", emptyList(), t.id, t.poster.orEmpty(), t.overview.orEmpty(),
        t.released?.get(Calendar.YEAR), t.genres.map { it.name }, provider, t.banner.orEmpty(), listOf(MediaReference(provider, t.id, "Série")), t.imdbId)
}

data class ExternalSource(val name: String, val url: String)
data class MediaReference(val provider: String, val id: String, val tag: String)
data class Anime(val title: String, val tag: String, val episodes: List<Episode>, val id: String = "", val poster: String = "",
    val description: String = "", val year: Int? = null, val genres: List<String> = emptyList(), val provider: String = "",
    val banner: String = "", val references: List<MediaReference> = emptyList(), val imdbId: String? = null)
data class Episode(val title: String, val description: String, val sources: List<VideoSource>, val id: String = "",
    val number: Int = 1, val seasonNumber: Int = 1, val poster: String = "", val language: String = "VF")
data class VideoSubtitle(val url: String, val language: String = "fr", val label: String = "Français", val mimeType: String = "text/vtt")
data class VideoSource(val name: String, val url: String, val language: String, val quality: String, val provider: String,
    val headers: Map<String, String> = emptyMap(), val subtitles: List<VideoSubtitle> = emptyList(), val mimeType: String? = null,
    val latencyMs: Long = -1, val reachable: Boolean = true, val serverId: String = "")
