package fr.nekotv

import android.content.Context
import com.streamflixreborn.streamflix.StreamFlixApp
import com.streamflixreborn.streamflix.providers.Provider
import com.streamflixreborn.streamflix.providers.ProviderConfigUrl
import com.streamflixreborn.streamflix.providers.IptvProvider
import com.streamflixreborn.streamflix.providers.TvSamaAnimeProvider
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
    private val defaultExternalSources = listOf(
        ExternalSource("FRAnime", "https://franime.fr/"),
        ExternalSource("AnimeOVF", "https://animeovf.fr/", "Accès par compte · catalogue intégré indisponible"),
        ExternalSource("Coflix", "https://coflix.ac/", "Protection antibot · catalogue intégré indisponible"),
        ExternalSource("AnimeKO", "https://animeko.ws/"),
        ExternalSource("AniWatch", "https://aniwatch.co.ba/"),
        ExternalSource("VoirAnime", "https://voir-anime.to/"),
        ExternalSource("ActVid", "https://actvid.sx/home"),
        ExternalSource("BlablaStream", "https://blablastream.fr/"),
        ExternalSource("DessinAnime", "https://dessinanime.cc/"),
        ExternalSource("FrenchStreaming", "https://www.french-streaming.tv/"),
        ExternalSource("TFX73", "https://tfx73.lol/"),
        ExternalSource("WarFlix", "https://warflix.im/film/1423191")
    )
    private val externalSources get() = defaultExternalSources.map { it.copy(url = com.streamflixreborn.streamflix.utils.SourceAddresses.current(it.name, it.url)) }
    private val providers by lazy { Provider.providers.keys.filter { it.language == "fr" } }
    private val preferences by lazy { StreamFlixApp.instance.getSharedPreferences("tvsama_settings", Context.MODE_PRIVATE) }
    private val statuses = ConcurrentHashMap<String, String>()
    private val health = ConcurrentHashMap<String, String>()
    private val details = ConcurrentHashMap<String, Anime>()
    private val streamCache = ConcurrentHashMap<String, Pair<Long, List<VideoSource>>>()
    private val requests = Semaphore(4)
    private val extractors = Semaphore(4)
    private val detailRequests = Semaphore(3)
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
        details.clear()
        if (!enabled) { health.remove(name); statuses.remove(name) }
    }
    fun providerStatuses(): Map<String, String> = statuses.toMap()
    fun sourceHealth(): Map<String, String> = health.filterKeys(::isProviderEnabled)
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
        .replace(Regex("\\(\\s*\\)|\\[\\s*\\]"), " ")
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
        val words = folded.split(' ').filter { it.isNotBlank() }
        return listOf(trimmed, folded, folded.replace(" ", ""), words.joinToString("-"))
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
        val isAnimationCategory = cat.contains("anime") || cat.contains("animé") || cat.contains("animation")
        val selected = providers.filter { isProviderEnabled(it.name) }.filter {
            when {
                cat.contains("direct") || cat.contains("tv en") -> it is IptvProvider
                query.isNotBlank() -> it !is IptvProvider
                isAnimationCategory -> (it is TvSamaAnimeProvider && it.animation) || it.name.contains("anime", true) || it.name.contains("manga", true)
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
            requestedLanguage == null || languageHint(item.title) == null || languageHint(item.title) == requestedLanguage
        }
        CatalogIdentity.merge(languageFiltered.map { it.copy(title = displayTitle(it.title)) })
    }

    suspend fun loadDetails(anime: Anime): Anime = withContext(Dispatchers.IO) {
        val relatedCards = if (anime.tag == "Série") searchAllProviders(CatalogIdentity.numberedBase(anime.title), "Séries")
            .filter { CatalogIdentity.title(it.title) == CatalogIdentity.title(CatalogIdentity.seriesTitle(anime.title)) ||
                (it.references.any { ref -> ref.id == anime.id && ref.provider == anime.provider }) }
            .filter { card -> anime.year == null || card.year == null || anime.year == card.year ||
                card.references.any { it.id == anime.id && it.provider == anime.provider } } else emptyList()
        val seriesTitle = relatedCards.firstOrNull { card -> card.references.any { it.id == anime.id && it.provider == anime.provider } }?.title ?: anime.title
        val related = relatedCards.flatMap { it.references }
        val references = (listOf(MediaReference(anime.provider, anime.id, anime.tag)) + anime.references + related)
            .distinct().filter { isProviderEnabled(it.provider) }
        val loaded = references.map { ref -> async {
            detailRequests.withPermit {
                try {
                    loadProviderDetails(anime.copy(title = seriesTitle, provider = ref.provider, id = ref.id, tag = ref.tag,
                        references = listOf(ref))).takeIf { it.episodes.isNotEmpty() }
                } catch (e: TimeoutCancellationException) {
                    currentCoroutineContext().ensureActive(); statuses[ref.provider] = "Délai dépassé"; null
                } catch (e: CancellationException) { throw e
                } catch (e: Exception) {
                    statuses[ref.provider] = "Fiche indisponible • ${e.message?.take(60).orEmpty()}"; null
                }
            }
        } }.awaitAll().filterNotNull()
        val first = loaded.firstOrNull() ?: error("Aucune fiche disponible sur les sources activées. Réessayez ou actualisez les sources.")
        first.copy(title = if (first.tag == "Série") CatalogIdentity.seriesTitle(seriesTitle) else first.title,
            references = references,
            episodes = loaded.flatMap { it.episodes }.distinctBy { "${it.seasonNumber}|${it.number}|${it.language}" }
                .sortedWith(compareBy<Episode> { it.seasonNumber }.thenBy { it.number }),
            imdbId = loaded.firstNotNullOfOrNull { it.imdbId })
    }

    private suspend fun loadProviderDetails(anime: Anime): Anime {
        details[identity(anime) + "|" + anime.title]?.let { return it.copy(references = anime.references) }
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
                            Episode(title, ep.overview.orEmpty(), emptyList(), ep.id, ep.number,
                                if (tv.seasons.size == 1 && season.number <= 1)
                                    CatalogIdentity.seasonNumber(tv.title, anime.title) ?: season.number
                                else season.number.coerceAtLeast(0), artwork(ep.poster, provider.name), episodeLanguage(title, ep.overview.orEmpty(), provider.language))
                        } to eps.isEmpty()
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
        val result = enrichWithTmdb(enrichSeries(loaded.copy(references = anime.references,
            poster = loaded.poster.ifBlank { anime.poster }, banner = loaded.banner.ifBlank { anime.banner })))
        // A partial response remains retryable instead of poisoning the detail cache.
        if (!partial && result.episodes.isNotEmpty()) details[identity(anime) + "|" + anime.title] = result
        return result
    }
    suspend fun getDetails(anime: Anime) = loadDetails(anime)

    suspend fun refreshDirectory(): List<DirectorySource> {
        val entries = SourceDirectory.loadFrench()
        val aliases = mapOf("Wiflix" to "flemmix", "MyFluneo" to "fluneo",
            "FrenchStream" to "french-stream.one", "FrenchManga" to "french-stream-manga", "AnimeOVF" to "animeo")
        fun key(value: String) = value.lowercase().filter { it.isLetterOrDigit() }
        val historicalBases = mapOf("AnimeKO" to "https://animeko.ws/", "Wiflix" to "https://flemmix.team/")
        val sources = providers.map { it.name to it.baseUrl } + externalSources.map { it.name to it.url }
        for ((name, previous) in sources) {
            val entry = entries.singleOrNull { key(it.slug) == key(aliases[name] ?: name) }
                ?: entries.singleOrNull { key(it.name) == key(name) } ?: continue
            historicalBases[name]?.let { com.streamflixreborn.streamflix.utils.SourceAddresses.update(name, it, entry.url) }
            if (com.streamflixreborn.streamflix.utils.SourceAddresses.update(name, previous, entry.url)) {
                providers.firstOrNull { it.name == name }?.let { provider ->
                    UserPreferences.setProviderCache(provider, UserPreferences.PROVIDER_URL, entry.url.trimEnd('/') + "/")
                }
            }
        }
        details.clear(); streamCache.clear()
        return entries
    }

    suspend fun refreshSources() = withContext(Dispatchers.IO) {
        try { refreshDirectory(); health.remove("Annuaire") }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { health["Annuaire"] = "✕ Actualisation impossible : ${e.message.orEmpty().take(80)}" }
        details.clear(); streamCache.clear()
        val providerRefreshes = providers.filter { isProviderEnabled(it.name) }.map { p -> async {
            requests.withPermit {
                health[p.name] = "Vérification…"
                try {
                    withTimeout(25_000) {
                        if (p is ProviderConfigUrl) p.onChangeUrl(false)
                        check(p.getHome().any { it.list.isNotEmpty() }) { "Catalogue vide" }
                    }
                    health[p.name] = "✓ Catalogue disponible · lecture à vérifier"
                } catch (e: TimeoutCancellationException) {
                    currentCoroutineContext().ensureActive()
                    health[p.name] = "✕ Délai dépassé"
                } catch (e: CancellationException) { throw e
                } catch (e: Exception) {
                    health[p.name] = "✕ ${e.message?.take(100) ?: "Indisponible"}"
                }
            }
        } }
        val externalRefreshes = externalSources.filter { isProviderEnabled(it.name) }.map { source -> async {
            requests.withPermit { checkExternalSource(source) }
        } }
        (providerRefreshes + externalRefreshes).awaitAll()
        Unit
    }

    private suspend fun checkExternalSource(source: ExternalSource) {
        health[source.name] = "Vérification…"
        try {
            withTimeout(12_000) {
                val connection = (URL(source.url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5_000
                    readTimeout = 5_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "TvSama/1.0 AndroidTV")
                }
                try {
                    check(connection.responseCode in 200..399) { "HTTP ${connection.responseCode}" }
                } finally { connection.disconnect() }
            }
            health[source.name] = "✓ Site joignable · lecture non vérifiée"
        } catch (e: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            health[source.name] = "✕ Délai dépassé"
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            health[source.name] = "✕ ${e.message?.take(100) ?: "Site indisponible"}"
        }
    }

    suspend fun resolveSources(anime: Anime, episode: Episode? = null, language: String = "Toutes", refresh: Boolean = false): List<VideoSource> = withContext(Dispatchers.IO) {
        if (anime.episodes.isEmpty()) return@withContext resolveSources(loadDetails(anime), episode, language, refresh)
        val selected = episode ?: anime.episodes.firstOrNull() ?: return@withContext emptyList()
        val cacheKey = "${identity(anime)}|${selected.seasonNumber}|${selected.number}|${selected.id}|$language"
        if (!refresh) streamCache[cacheKey]?.takeIf { System.currentTimeMillis() - it.first < 120_000 }?.let { return@withContext it.second }
        val known = anime.references.ifEmpty { listOf(MediaReference(anime.provider, anime.id, anime.tag)) }
        // Old favourites and provider-specific cards also need newly added language sources.
        val discovered = if (language == "VF" && anime.tag == "Série" &&
            known.none { it.provider == "Animes-Sama" } && isProviderEnabled("Animes-Sama")) {
            attempt("Animes-Sama", emptyList<MediaReference>()) {
                com.streamflixreborn.streamflix.providers.AnimesSamaProvider.search(anime.title).filterIsInstance<TvShow>()
                    .filter { CatalogIdentity.title(it.title) == CatalogIdentity.title(anime.title) }
                    .map { MediaReference("Animes-Sama", it.id, "Série") }
            }
        } else emptyList()
        val refs = (known + discovered).distinct()
        val sources = refs.filter { isProviderEnabled(it.provider) }.map { ref -> async {
            extractionContext.withLock { attempt(ref.provider, emptyList<VideoSource>(), 120_000) {
                val p = owner(ref.provider)
                if (UserPreferences.currentProvider?.name != p.name) UserPreferences.currentProvider = p
                val matching = loadProviderDetails(anime.copy(id = ref.id, provider = ref.provider,
                    tag = ref.tag, episodes = emptyList(), references = listOf(ref)))
                val candidates = matching.episodes.filter { it.number == selected.number && it.seasonNumber == selected.seasonNumber }
                val ep = candidates.firstOrNull { it.language == language } ?: candidates.firstOrNull { it.id == selected.id } ?: candidates.firstOrNull()
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
                                val label = "${server.name} ${ep.language.takeUnless { it == "UNKNOWN" }.orEmpty()}"
                                val serverLanguage = languageHint(server.name)
                                if (language != "Toutes" && serverLanguage != null && serverLanguage != language) return@withTimeout null
                                val video = p.getVideo(server)
                        val lang = (serverLanguage ?: detectLanguage(label, video, p)).let { known ->
                            if (known != "UNKNOWN") known else languageHint(runCatching { java.net.URI(matching.id.substringBefore('#')).path.orEmpty().substringAfterLast('/') }.getOrDefault("")) ?: "UNKNOWN"
                        }
                        if (language != "Toutes" && lang != language) return@withTimeout null
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
                requestMethod = "GET"
                setRequestProperty("Range", "bytes=0-1023")
                connectTimeout = 5_000
                readTimeout = 5_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "TvSama/1.0 AndroidTV")
                source.headers.forEach { (key, value) -> setRequestProperty(key, value) }
                connect()
            }
        }.getOrNull() ?: return@withContext source.copy(latencyMs = -1, reachable = false)
        return@withContext try {
            val code = connection.responseCode
            val latency = ((System.nanoTime() - started) / 1_000_000L).coerceAtLeast(1L)
            val readable = code in 200..299 && !connection.contentType.orEmpty().contains("text/html", true) &&
                connection.inputStream.use { it.read() } >= 0
            source.copy(latencyMs = if (readable) latency else -1, reachable = readable)
        } catch (_: Exception) {
            source.copy(latencyMs = -1, reachable = false)
        } finally { connection.disconnect() }
    }
    private fun artwork(value: String?, provider: String): String {
        if (value.isNullOrBlank()) return ""
        val base = owner(provider).baseUrl
        val url = runCatching { java.net.URI(base).resolve(value.trim()).toString() }.getOrDefault("")
        if (!url.startsWith("http")) return ""
        if (url.contains("sf_headers=")) return url
        return com.streamflixreborn.streamflix.utils.ArtworkRequestHeaders.withHeaders(url,
            referer = base, userAgent = com.streamflixreborn.streamflix.utils.NetworkClient.USER_AGENT).orEmpty()
    }
    private fun movie(m: Movie, provider: String) = Anime(m.title, "Film", listOf(Episode("Film", m.overview.orEmpty(), emptyList(), m.id, 1, 1, artwork(m.poster, provider))),
        m.id, artwork(m.poster, provider), m.overview.orEmpty(), m.released?.get(Calendar.YEAR), m.genres.map { it.name },
        provider, artwork(m.banner, provider), listOf(MediaReference(provider, m.id, "Film")), m.imdbId)
    private fun show(t: TvShow, provider: String) = Anime(t.title, "Série", emptyList(), t.id, artwork(t.poster, provider), t.overview.orEmpty(),
        t.released?.get(Calendar.YEAR), t.genres.map { it.name }, provider, artwork(t.banner, provider), listOf(MediaReference(provider, t.id, "Série")), t.imdbId)
}

data class ExternalSource(val name: String, val url: String, val limitation: String = "Lien externe · pas de catalogue intégré")
data class MediaReference(val provider: String, val id: String, val tag: String)
data class Anime(val title: String, val tag: String, val episodes: List<Episode>, val id: String = "", val poster: String = "",
    val description: String = "", val year: Int? = null, val genres: List<String> = emptyList(), val provider: String = "",
    val banner: String = "", val references: List<MediaReference> = emptyList(), val imdbId: String? = null,
    val firstAired: String? = null, val lastAired: String? = null, val seriesStatus: String? = null, val metadataUrl: String? = null)
data class Episode(val title: String, val description: String, val sources: List<VideoSource>, val id: String = "",
    val number: Int = 1, val seasonNumber: Int = 1, val poster: String = "", val language: String = "UNKNOWN", val introSeason: Int? = null, val introNumber: Int? = null)
data class VideoSubtitle(val url: String, val language: String = "fr", val label: String = "Français", val mimeType: String = "text/vtt")
data class VideoSource(val name: String, val url: String, val language: String, val quality: String, val provider: String,
    val headers: Map<String, String> = emptyMap(), val subtitles: List<VideoSubtitle> = emptyList(), val mimeType: String? = null,
    val latencyMs: Long = -1, val reachable: Boolean = true, val serverId: String = "")
