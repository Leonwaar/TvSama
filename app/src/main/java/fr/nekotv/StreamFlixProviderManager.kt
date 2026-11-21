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
        ExternalSource("Vidrift", "https://vidrift.net/", "Lecteur officiel intégré · VF et VOSTFR selon les pistes confirmées"),
        ExternalSource("FRAnime", "https://franime.fr/"),
        ExternalSource("AnimeOVF", "https://animeovf.fr/", "Accès par compte · catalogue intégré indisponible"),
        ExternalSource("Coflix", "https://coflix.ac/", "Protection antibot · catalogue intégré indisponible"),
        ExternalSource("AnimeKO", "https://animeko.ws/"),
        ExternalSource("AniWatch", "https://aniwatch.co.ba/"),
        ExternalSource("VoirAnime", "https://voir-anime.to/"),
        ExternalSource("ActVid", "https://actvid.sx/home"),
        ExternalSource("DessinAnime", "https://dessinanime.cc/"),
        ExternalSource("FrenchStreaming", "https://www.french-streaming.tv/"),
        ExternalSource("WarFlix", "https://warflix.im/film/1423191")
    )
    private val externalSources get() = defaultExternalSources.filterNot { external -> providers.any { it.name.equals(external.name, true) } }
        .map { it.copy(url = com.streamflixreborn.streamflix.utils.SourceAddresses.current(it.name, it.url)) }
    /** Every registered adapter is eligible for catalogue search and native playback.
     * Language is decided from the returned title/track, not from the site's default UI language. */
    private val providers by lazy { Provider.providers.keys.toList() }
    private val preferences by lazy { StreamFlixApp.instance.getSharedPreferences("tvsama_settings", Context.MODE_PRIVATE) }
    private val statuses = ConcurrentHashMap<String, String>()
    private val health = ConcurrentHashMap<String, String>()
    private val addressWarnings = ConcurrentHashMap<String, String>()
    private val addressChecks = Semaphore(6)
    private val directoryLock = Mutex()
    private val refreshLock = Mutex()
    private var directoryEntries = emptyList<DirectorySource>()
    private var directoryCheckedAt = 0L
    private val directoryResults = ConcurrentHashMap<String, String>()
    fun directoryReport(): Map<String, String> = directoryResults.toSortedMap()
    private val details = ConcurrentHashMap<String, Anime>()
    private val streamCache = ConcurrentHashMap<String, Pair<Long, List<VideoSource>>>()
    private fun <K, V> evict(map: ConcurrentHashMap<K, V>, limit: Int) {
        if (map.size >= limit) {
            val keys = map.keys().toList()
            keys.take(limit / 4).forEach { map.remove(it) }
        }
    }
    private val rejectedShortMedia: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val serverCache = ConcurrentHashMap<String, Pair<Long, List<Video.Server>>>()
    private val titleAliases = ConcurrentHashMap<String, List<String>>()
    private val relatedLookups = ConcurrentHashMap<String, Pair<Long, List<Anime>>>()
    private val relatedLookupLock = Mutex()
    // Search is I/O bound. Keep enough concurrent slots for the first results to
    // arrive quickly while still bounding provider pressure.
    private val requests = Semaphore(12)
    private val extractors = Semaphore(4)
    private val detailRequests = Semaphore(3)
    private var currentName: String? = null

    private fun protectedAccess(message: String?): Boolean = message.orEmpty().matches(
        Regex("(?i).*(captcha|cloudflare|validation|à valider|accès protégé|protection antibot|http\\s*(401|403|429)).*")
    )

    companion object {
        private val singleton by lazy { StreamFlixProviderManager() }
        fun getInstance() = singleton
    }
    suspend fun getProviders() = providers.toList()
    suspend fun getProviderNames() = providers.map { it.name } + externalSources.map { it.name }
    fun externalSource(name: String) = externalSources.firstOrNull { it.name == name }
    fun sourceUrl(name: String) = providers.firstOrNull { it.name == name }?.baseUrl ?: externalSource(name)?.url
    fun accessUrl(name: String) = com.streamflixreborn.streamflix.utils.SourceAddresses.pending(name)
        ?: (if (name == "FRAnime") com.streamflixreborn.streamflix.providers.FrAnimeProvider.validationUrl() else null) ?: sourceUrl(name)
    /** Page source utilisée uniquement pour la validation intégrée (« Accès ») ; jamais comme
     *  repli de lecture : toute vidéo est lue par TvSamaPlayer. */
    fun browserUrl(anime: Anime, episode: Episode?): String? {
        com.streamflixreborn.streamflix.utils.SourceAddresses.pending(anime.provider)?.let { return it }
        if (anime.provider == "FRAnime") com.streamflixreborn.streamflix.providers.FrAnimeProvider.accessUrl(episode?.id ?: anime.id)?.let { return it }
        val id = episode?.id?.substringBefore('#')?.takeIf { it.startsWith("http") } ?: anime.id.substringBefore('#')
        val address = if (id.startsWith("http")) id else sourceUrl(anime.provider)?.let { base ->
            val path = when (anime.provider) {
                "Wiflix" -> (if (anime.tag == "Film") "film-en-streaming/" else "serie-en-streaming/") + anime.id
                "FrenchAnime" -> if (anime.tag == "Film") "films-vf-vostfr/${anime.id}.html"
                    else (episode?.id?.substringBeforeLast('_') ?: anime.id).removeSuffix(".html") + ".html"
                else -> anime.id
            }
            java.net.URI(base).resolve(path).toString()
        }
        return address?.let(com.streamflixreborn.streamflix.utils.SourceAddresses::rewrite)?.takeIf {
            runCatching { java.net.URI(it).scheme in listOf("https", "http") }.getOrDefault(false)
        }
    }
    fun isProviderEnabled(name: String) = name !in preferences.getStringSet("disabled_providers", emptySet()).orEmpty()
    @Synchronized
    fun setProviderEnabled(name: String, enabled: Boolean) {
        val disabled = preferences.getStringSet("disabled_providers", emptySet()).orEmpty().toMutableSet()
        if (enabled) disabled.remove(name) else disabled.add(name)
        preferences.edit().putStringSet("disabled_providers", disabled).apply()
        streamCache.clear()
        serverCache.clear()
        details.clear(); relatedLookups.clear()
        if (!enabled) { health.remove(name); statuses.remove(name) }
    }
    fun providerStatuses(): Map<String, String> = statuses.toMap()
    fun sourceHealth(): Map<String, String> = (health.keys + addressWarnings.keys).filter(::isProviderEnabled).associateWith { name ->
        listOfNotNull(addressWarnings[name]?.let { "⚠ $it" }, health[name]).joinToString(" • ")
    }
    fun rejectShortStream(source: VideoSource) {
        rejectedShortMedia.add(source.url)
        streamCache.replaceAll { _, cached -> cached.first to cached.second.filterNot { it.url == source.url } }
        health[source.provider] = "⚠ Lecteur écarté : extrait court ou durée inconnue · essayez un autre lecteur"
    }
    fun confirmProgramPlayback(source: VideoSource, durationMs: Long) {
        if (durationMs >= 90_000 && source.url !in rejectedShortMedia)
            health[source.provider] = "✓ Lecture confirmée sur un programme pendant 90 s · autres titres non testés"
    }
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
        return when (marker) { "vostfr", "vost" -> "VOSTFR"; "vo" -> "OTHER"; else -> "VF" }
    }

    private fun searchQueries(query: String): List<String> {
        val trimmed = query.trim()
        val folded = Normalizer.normalize(trimmed.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val words = folded.split(' ').filter { it.isNotBlank() }
        return (listOf(trimmed) + TitleAliases.variants(trimmed) + titleAliases[CatalogIdentity.title(trimmed)].orEmpty() + listOf(folded, folded.replace(" ", ""), words.joinToString("-")))
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
    }

    private suspend fun <T> attempt(name: String, fallback: T, timeoutMillis: Long = 25_000, block: suspend () -> T): T = try {
        withTimeout(timeoutMillis) { block() }.also { statuses[name] = "Disponible" }
    } catch (e: TimeoutCancellationException) {
        currentCoroutineContext().ensureActive()
        statuses[name] = "Délai dépassé"; fallback
    } catch (e: CancellationException) { throw e
    } catch (e: Exception) {
        val protected = protectedAccess(e.message)
        if (protected && health[name]?.startsWith("✓") != true) health[name] = "⚠ Validation du site requise · ouvrez « Accès »"
        statuses[name] = "${if (protected) "Validation requise" else "Indisponible"} • ${e.message?.take(70) ?: "erreur réseau"}"
        fallback
    }

    suspend fun searchAllProviders(query: String, category: String, page: Int = 1, includePopularity: Boolean = true,
        onPartial: (suspend (List<Anime>) -> Unit)? = null): List<Anime> = withContext(Dispatchers.IO) {
        val partialMutex = Mutex()
        val partialItems = mutableListOf<Anime>()
        val cat = category.lowercase()
        val isAnimationCategory = cat.contains("anime") || cat.contains("animé") || cat.contains("animation")
        val selected = providers.filter { isProviderEnabled(it.name) }.filter {
            when {
                cat == "tv" || cat.contains("direct") || cat.contains("tv en") -> it is IptvProvider
                query.isNotBlank() -> it !is IptvProvider && (!cat.contains("film") || Provider.supportsMovies(it))
                isAnimationCategory -> it is TvSamaAnimeProvider || it.name.contains("anime", true) || it.name.contains("manga", true)
                else -> it !is IptvProvider
            }
        }
        val popularity = if (includePopularity) async { cataloguePopularity(query) } else null
        val found = selected.map { provider -> async {
            val published = mutableListOf<com.streamflixreborn.streamflix.adapters.AppAdapter.Item>()
            val result = requests.withPermit { attempt(
                provider.name,
                published,
                timeoutMillis = if (query.isNotBlank()) 20_000L else 12_000L
            ) {
                val items = when {
                    query.isNotBlank() -> {
                        // Exact title and the first canonical alias cover the normal
                        // path. The folded variants remain available as the third
                        // fallback without multiplying the latency on every source.
                        val variants = searchQueries(query).take(5)
                        published += provider.search(variants.first(), page)
                        // Keep alias/folded queries available for providers with fragile
                        // search indexes. Cap only the accumulated catalogue, never the
                        // decision to try another query variant.
                        for (variant in variants.drop(1)) {
                            if (published.size >= 24) break
                            published += provider.search(variant, page)
                        }
                        published
                    }
                    cat.contains("film") -> if (Provider.supportsMovies(provider)) provider.getMovies(page) else emptyList()
                    cat.contains("série") || cat.contains("serie") -> if (Provider.supportsTvShows(provider)) provider.getTvShows(page) else emptyList()
                    page > 1 -> (if (Provider.supportsMovies(provider)) provider.getMovies(page) else emptyList()) +
                        (if (Provider.supportsTvShows(provider)) provider.getTvShows(page) else emptyList())
                    else -> provider.getHome().flatMap { it.list }
                }
                items.toMutableList()
            } }.let { items -> attempt(provider.name, emptyList<Anime>(), 12_000) { items.mapNotNull { item -> when(item) {
                    is Movie -> movie(item, provider.name)
                    is TvShow -> show(item, provider.name)
                    is com.streamflixreborn.streamflix.models.Episode -> item.tvShow?.let { show(it, provider.name) }
                    else -> null
                } }.map { card ->
                    val aliases = (provider as? com.streamflixreborn.streamflix.providers.MediaAliasesProvider)?.aliases(card.id).orEmpty()
                    if (aliases.isNotEmpty()) (aliases + card.title).forEach { titleAliases[CatalogIdentity.title(it)] = aliases }
                    card.copy(aliases = aliases)
                }.filter { a -> a.id !in setOf("creador-info", "apoyo-nando") }.filter { a ->
                    when { cat.contains("film") -> a.tag == "Film"
                        cat.contains("série") || cat.contains("serie") -> a.tag != "Film"
                        else -> true }
                }.distinctBy { identity(it) } } }
            if (onPartial != null && result.isNotEmpty()) partialMutex.withLock {
                partialItems.addAll(result)
                onPartial(rankCatalogue(CatalogIdentity.merge(partialItems.map { it.copy(title = displayTitle(it.title)) }), query))
            }
            result
                } }.awaitAll().let { buckets ->
                    (0 until (buckets.maxOfOrNull { it.size } ?: 0)).flatMap { index -> buckets.mapNotNull { it.getOrNull(index) } }
                }
        val requestedLanguage = when {
            cat.contains("vost") || cat.contains("vo sous") -> "VOSTFR"
            cat == "vf" || cat.contains("version française") -> "VF"
            else -> null
        }
        val languageFiltered = found.filter { item ->
            requestedLanguage == null || languageHint(item.title) == null || languageHint(item.title) == requestedLanguage
        }
        rankCatalogue(CatalogIdentity.merge(languageFiltered.map { it.copy(title = displayTitle(it.title)) }), query, popularity?.await().orEmpty())
    }

    private suspend fun discoverRelatedCards(anime: Anime): List<Anime> {
        if (anime.isLiveContent()) return emptyList()
        return relatedLookupLock.withLock {
            val key = "${anime.tag}|${CatalogIdentity.title(anime.title)}|${anime.year}"
            relatedLookups[key]?.takeIf { System.currentTimeMillis() - it.first <
                if (it.second.isEmpty()) 15_000 else 120_000 }?.let { return@withLock it.second }
            val queries = (listOf(if (anime.tag == "Série") CatalogIdentity.numberedBase(anime.title) else anime.title) + anime.aliases)
                .filter(String::isNotBlank).distinctBy(CatalogIdentity::title).take(2)
            val cards = mutableListOf<Anime>()
            withTimeoutOrNull(18_000) {
                for (query in queries) {
                    var partial = emptyList<Anime>()
                    var result = emptyList<Anime>()
                    try {
                        result = withTimeoutOrNull(if (queries.size > 1) 9_000 else 18_000) {
                            searchAllProviders(query, if (anime.tag == "Série") "Séries" else "Films",
                                includePopularity = false, onPartial = { partial = it })
                        } ?: partial
                    } finally {
                        // Keep discoveries even when the enclosing title budget expires.
                        cards += result.ifEmpty { partial }
                    }
                }
            }
            val matched = relatedCatalogueMatches(anime, cards)
            if (relatedLookups.size >= 64) relatedLookups.entries.minByOrNull { it.value.first }?.let { relatedLookups.remove(it.key) }
            relatedLookups[key] = System.currentTimeMillis() to matched
            matched
        }
    }

    suspend fun loadDetails(anime: Anime, onPartial: (suspend (Anime) -> Unit)? = null): Anime = withContext(Dispatchers.IO) {
        // A merged card may display a film while its catalogue reference is still a series
        // page (or the reverse). Always load the page with the tag the provider published.
        val primaryRef = anime.references.firstOrNull { it.provider == anime.provider && it.id == anime.id }
            ?: MediaReference(anime.provider, anime.id, anime.tag)
        val primary = if (isProviderEnabled(anime.provider)) async {
            val item = try { loadProviderDetails(anime.copy(tag = primaryRef.tag, references = listOf(primaryRef))).takeIf { it.episodes.isNotEmpty() } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { statuses[anime.provider] = "Fiche indisponible • ${e.message?.take(60).orEmpty()}"; null }
            if (item != null) onPartial?.invoke(item.copy(references = (listOf(primaryRef) + anime.references)
                .distinct().filter { isProviderEnabled(it.provider) }))
            item
        } else null
        val relatedCards = discoverRelatedCards(anime)
        val seriesTitle = relatedCards.firstOrNull { card -> card.references.any { it.id == anime.id && it.provider == anime.provider } }?.title ?: anime.title
        val related = relatedCards.flatMap { it.references }
        val references = (listOf(primaryRef) + anime.references + related)
            .distinct().filter { isProviderEnabled(it.provider) }
        fun combine(items: List<Anime>): Anime {
            val loaded = items.sortedBy { item -> references.indexOfFirst { it.provider == item.provider && it.id == item.id } }
            val first = loaded.first()
            return first.copy(title = if (first.tag == "Série") CatalogIdentity.seriesTitle(seriesTitle) else first.title,
                poster = anime.poster.ifBlank { first.poster }, banner = anime.banner.ifBlank { first.banner },
                references = references,
                episodes = loaded.flatMap { it.episodes }.distinctBy { "${it.seasonNumber}|${it.number}|${it.language}" }
                    .sortedWith(compareBy<Episode> { it.seasonNumber }.thenBy { it.number }),
                aliases = (anime.aliases + loaded.flatMap { it.aliases }).distinct(),
                imdbId = loaded.firstNotNullOfOrNull { it.imdbId },
                tmdbId = loaded.mapNotNull { it.tmdbId }.distinct().singleOrNull(),
                originalLanguage = loaded.firstNotNullOfOrNull { it.originalLanguage } ?: anime.originalLanguage)
        }
        val completed = mutableListOf<Anime>()
        val completedLock = Mutex()
        withTimeoutOrNull(45_000) { coroutineScope {
            references.map { ref -> launch {
                val item = if (ref == primaryRef && primary != null) primary.await() else detailRequests.withPermit {
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
                if (item != null) {
                    val partial = completedLock.withLock { completed += item; combine(completed) }
                    if (ref != primaryRef) onPartial?.invoke(partial)
                }
            } }.joinAll()
        } }
        val combined = completedLock.withLock { completed.takeIf { it.isNotEmpty() }?.let(::combine) }
            ?: error("Aucune fiche disponible sur les sources activées. Réessayez ou actualisez les sources.")
        if (combined.isLiveContent()) combined else withTimeoutOrNull(3_000) { enrichWithTmdb(enrichSeries(combined)) } ?: combined
    }

    private suspend fun applyPendingAddress(provider: Provider) {
        directoryLock.withLock {
            val name = provider.name
            com.streamflixreborn.streamflix.utils.SourceAddresses.pending(name)?.let { candidate ->
                val replacement = withTimeout(8_000) {
                    com.streamflixreborn.streamflix.providers.SourceAddressValidation.resolve(name, candidate)
                } ?: error("La nouvelle adresse n’a pas encore fourni de catalogue compatible")
                com.streamflixreborn.streamflix.utils.SourceAddresses.update(name, provider.baseUrl, replacement)
                UserPreferences.setProviderCache(provider, UserPreferences.PROVIDER_URL, replacement)
                (provider as? com.streamflixreborn.streamflix.providers.CatalogueCacheProvider)?.clearCatalogueCache()
                addressWarnings.remove(name)
                details.entries.removeIf { it.value.provider == name }
                serverCache.clear(); streamCache.clear()
            }
        }
    }
    suspend fun retrySourceAccess(name: String) {
        providers.firstOrNull { it.name == name }?.let { applyPendingAddress(it) }
    }

    private suspend fun loadProviderDetails(anime: Anime): Anime {
        val provider = owner(anime.provider)
        details[identity(anime) + "|" + anime.title]?.let { return it.copy(references = anime.references) }
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
        val result = loaded.copy(references = anime.references, imdbId = loaded.imdbId ?: anime.imdbId,
            aliases = (anime.aliases + (provider as? com.streamflixreborn.streamflix.providers.MediaAliasesProvider)?.aliases(anime.id).orEmpty()).distinct(),
            poster = loaded.poster.ifBlank { anime.poster }, banner = loaded.banner.ifBlank { anime.banner },
            description = loaded.description.ifBlank { TvSamaAnimeProvider.cleanSynopsis(anime.description) },
            genres = loaded.genres.ifEmpty { anime.genres }, year = loaded.year ?: anime.year,
            firstAired = loaded.firstAired ?: anime.firstAired,
            popularity = maxOf(loaded.popularity, anime.popularity),
            rating = if (loaded.voteCount > 0) loaded.rating else anime.rating,
            voteCount = maxOf(loaded.voteCount, anime.voteCount))
        // A partial response remains retryable instead of poisoning the detail cache.
        if (!partial && result.episodes.isNotEmpty()) {
            evict(details, 48)
            details[identity(anime) + "|" + anime.title] = result
        }
        return result
    }
    suspend fun getDetails(anime: Anime) = loadDetails(anime)
    suspend fun findCinemaMovie(anime: Anime): Anime {
        val names = (listOf(anime.title) + anime.aliases).map(CatalogIdentity::title).toSet()
        fun matches(cards: List<Anime>) = cards.filter { it.tag == "Film" && (it.year == null || anime.year == null || it.year == anime.year) &&
            (listOf(it.title) + it.aliases).any { title -> CatalogIdentity.title(title) in names } }
        var partial = emptyList<Anime>()
        val cards = withTimeoutOrNull(8_000) {
            searchAllProviders(anime.title, "Films", onPartial = { partial = matches(it) })
        } ?: partial
        val available = CatalogIdentity.merge(matches(cards)).firstOrNull()
            ?: error("Aucune source activée ne propose ce film actuellement. Sa sortie cinéma reste consultable.")
        return available.copy(poster = anime.poster.ifBlank { available.poster }, description = available.description.ifBlank { anime.description },
            firstAired = anime.firstAired ?: available.firstAired, year = anime.year ?: available.year)
    }
    suspend fun cinemaCandidates(): List<Anime> = withContext(Dispatchers.IO) {
        providers.filter { it.name in setOf("WarFlix", "Coflix", "Wiflix") && isProviderEnabled(it.name) }.map { provider -> async {
            attempt(provider.name, emptyList<Anime>(), 6_000) {
                val movies = if (provider.name == "WarFlix") com.streamflixreborn.streamflix.providers.WarFlixProvider.cinemaMovies() else provider.getMovies(1)
                movies.map { movie(it, provider.name).copy(aliases = (provider as? com.streamflixreborn.streamflix.providers.MediaAliasesProvider)?.aliases(it.id).orEmpty()) }
            }
        } }.awaitAll().flatten()
    }

    suspend fun refreshDirectory(force: Boolean = true): List<DirectorySource> = directoryLock.withLock {
        if (!force && System.currentTimeMillis() - directoryCheckedAt < 5 * 60_000L) return@withLock directoryEntries
        directoryResults.clear()
        val entries = try { SourceDirectory.loadFrench().also { directoryResults["OùStreamer"] = SourceDirectory.lastReport } }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            directoryResults["OùStreamer"] = "Annuaire indisponible · dernières adresses conservées, secours recherchés"
            directoryEntries.filter { it.origin == "OùStreamer" }
        }
        val backupEntries = java.util.Collections.synchronizedList(mutableListOf<DirectorySource>())
        val aliases = mapOf("Wiflix" to "wiflix", "MyFluneo" to "fluneo",
            "FrenchStream" to "french-stream.one", "FrenchManga" to "french-stream-manga", "AnimeOVF" to "animeo",
            "Papadustream" to "papadustream", "Anime-Sama" to "anime-sama", "Animes-Sama" to "animes-sama",
            "AnimeSite" to "animesite", "Vostfree" to "vostfree", "Anime-Ultime" to "animeultime", "FRAnime" to "franime", "Voiranime" to "voiranime",
            "Coflix" to "coflix", "Movix" to "movix", "Kidraz" to "kidraz", "AnimeKO" to "animeko")
        val historicalBases = mapOf("AnimeKO" to listOf("https://animeko.ws/"),
            "Wiflix" to listOf("https://flemmix.rip/", "https://flemmix.team/", "https://flemmix.style/"))
        val sources = providers.map { it.name to it.baseUrl } + defaultExternalSources.map { it.name to it.url }
        coroutineScope { sources.distinctBy { it.first.lowercase() }.map { (name, previous) -> async {
            addressChecks.withPermit {
                val completed = withTimeoutOrNull(45_000) {
                    val primary = selectDirectoryEntry(entries, aliases, name)
                    val provider = providers.firstOrNull { it.name == name }
                    if (!force && provider != null && UserPreferences.getProviderCache(provider, UserPreferences.PROVIDER_AUTOUPDATE) == "false")
                        return@withTimeoutOrNull
                    var issue = primary?.takeIf { it.status !in setOf("active", "redirected") }
                        ?.let { "${reportedDirectoryStatus(it.status)} (OùStreamer)" }
                    suspend fun validate(entry: DirectorySource): String? {
                        if (entry.status !in setOf("active", "redirected", "candidate")) return null
                        val compatibleAddress = if (name == "Anime-Ultime" && URL(entry.url).host.endsWith("anime-ultime.net"))
                            "https://v5.anime-ultime.net/" else entry.url
                        val candidate = if (provider != null && name != "Kidraz") URL(compatibleAddress).let { target ->
                            val path = URL(previous).path.takeUnless { it.isBlank() || it == "/" }
                                ?: (provider as? ProviderConfigUrl)?.defaultBaseUrl?.let { URL(it).path } ?: "/"
                            "${target.protocol}://${target.authority}${path.ifBlank { "/" }}"
                        } else compatibleAddress
                        return try {
                            withTimeout(if (name in setOf("Wiflix", "Coflix")) 25_000 else 10_000) {
                                com.streamflixreborn.streamflix.providers.SourceAddressValidation.resolve(name, candidate)
                            }
                        } catch (e: com.streamflixreborn.streamflix.providers.SourceValidationRequired) {
                            com.streamflixreborn.streamflix.utils.SourceAddresses.rememberPending(name, e.address)
                            issue = "Validation du site requise · ouvrez « Accès »"
                            null
                        } catch (e: TimeoutCancellationException) {
                            currentCoroutineContext().ensureActive(); issue = "Vérification trop lente · adresse précédente conservée"; null
                        } catch (e: CancellationException) { throw e }
                        catch (_: Exception) { issue = "Adresse proposée non vérifiable · adresse précédente conservée"; null }
                    }
                    var selectedEntry = primary
                    var replacement = primary?.let { validate(it) }
                    if (replacement == null) {
                        val backups = try { SourceDirectory.fallback(name) }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) {
                            directoryResults["Secours $name"] = "Annuaire de secours indisponible · aucune panne du site déduite"
                            emptyList()
                        }
                        backupEntries.addAll(backups)
                        for (backup in backups) {
                            val validated = validate(backup) ?: continue
                            replacement = validated; selectedEntry = backup; break
                        }
                    }
                    if (replacement == null) {
                        val wiki = try { SourceDirectory.wiki(name) }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) {
                            directoryResults["Wiki FMHY"] = "Annuaire de secours indisponible · aucune panne du site déduite"
                            emptyList()
                        }
                        backupEntries.addAll(wiki)
                        for (record in wiki) {
                            val validated = validate(record) ?: continue
                            replacement = validated; selectedEntry = record; break
                        }
                    }
                    val address = replacement
                    if (address == null) {
                        if (issue != null) addressWarnings[name] = issue!!
                        directoryResults[name] = issue ?: "Aucune nouvelle adresse validée · dernière adresse conservée"
                        return@withTimeoutOrNull
                    }
                    addressWarnings.remove(name)
                    historicalBases[name].orEmpty().forEach { com.streamflixreborn.streamflix.utils.SourceAddresses.update(name, it, address) }
                    val changed = com.streamflixreborn.streamflix.utils.SourceAddresses.update(name, previous, address)
                    provider?.let { UserPreferences.setProviderCache(it, UserPreferences.PROVIDER_URL, address) }
                    if (changed) {
                        details.entries.removeIf { it.value.provider == name }
                        (provider as? com.streamflixreborn.streamflix.providers.CatalogueCacheProvider)?.clearCatalogueCache()
                        relatedLookups.clear(); streamCache.clear(); serverCache.clear()
                    }
                    directoryResults[name] = if (changed) "Adresse mise à jour et mémorisée · ${selectedEntry?.origin} : $address"
                        else "Adresse confirmée · ${selectedEntry?.origin}"

                }
                if (completed == null) {
                    addressWarnings[name] = "Recherche d’adresse trop lente · dernière adresse conservée"
                    directoryResults[name] = "Vérification interrompue après 45 s · aucune panne du site déduite"
                }
        } } }.awaitAll() }
        directoryEntries = (entries + backupEntries.toList()).distinctBy { "${it.origin}|${it.slug}|${it.url}" }
        directoryCheckedAt = System.currentTimeMillis()
        directoryEntries
    }

    suspend fun refreshSources() = refreshLock.withLock { withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        rejectedShortMedia.clear()
        try { refreshDirectory(); health.remove("Annuaire") }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { health["Annuaire"] = "✕ Actualisation impossible : ${e.message.orEmpty().take(80)}" }
        details.clear(); streamCache.clear(); relatedLookups.clear()
        serverCache.clear()
        val checks = Semaphore(3)
        val providerRefreshes = providers.filter { isProviderEnabled(it.name) }.map { p -> async {
            checks.withPermit {
                health[p.name] = "Vérification…"
                if (com.streamflixreborn.streamflix.utils.SourceAddresses.pending(p.name) != null) {
                    health[p.name] = "⚠ Nouvelle adresse trouvée · validez-la dans « Accès »"
                    return@withPermit
                }
                try {
                    withTimeout(8_000) {
                        // The home page may already have fetched a fresh catalogue during directory lookup.
                        (p as? com.streamflixreborn.streamflix.providers.CatalogueCacheProvider)?.clearCatalogueCache(startedAt)
                        check(p.getHome().any { it.list.isNotEmpty() }) { "Catalogue vide" }
                    }
                    health[p.name] = "○ Catalogue accessible · aucune vidéo encore testée"
                } catch (e: TimeoutCancellationException) {
                    currentCoroutineContext().ensureActive()
                health[p.name] = "⚠ Site trop lent · réessayez plus tard"
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                    val message = e.message?.take(100) ?: "Indisponible"
                    health[p.name] = sourceFailureStatus(message)
                } finally {
                    if (health[p.name] == "Vérification…") health[p.name] = "○ Test interrompu · relancez la vérification"
                }
            }
        } }
        val externalRefreshes = externalSources.filter { isProviderEnabled(it.name) }.map { source -> async {
            checks.withPermit { checkExternalSource(source) }
        } }
        (providerRefreshes + externalRefreshes).awaitAll()
        Unit
    } }

    suspend fun verifySourceMedia(name: String): Boolean = withContext(Dispatchers.IO) {
        val provider = providers.firstOrNull { it.name == name } ?: return@withContext false
        health[name] = "Vérification du média…"
        details.entries.removeIf { it.value.provider == name }
        serverCache.clear(); streamCache.clear()
        try {
            applyPendingAddress(provider)
            val reference = if (name in setOf("Anime-Sama", "Animes-Sama", "Vostfree", "AnimeSite", "Voiranime", "MyFluneo", "FrenchManga"))
                withTimeoutOrNull(6_000) { provider.search("Naruto").filterIsInstance<TvShow>()
                    .filter { CatalogIdentity.title(it.title) == CatalogIdentity.title("Naruto") }.take(2) }.orEmpty()
            else emptyList()
            val home = try { withTimeout(8_000) { provider.getHome().flatMap { it.list.take(2) }.take(6) } }
                catch (e: TimeoutCancellationException) { currentCoroutineContext().ensureActive(); emptyList() }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { emptyList() }
            val cards = reference + home
            if (cards.isEmpty()) {
                health[name] = "⚠ Catalogue indisponible · aucun échantillon à tester"
                return@withContext false
            }
            var failure = ""
            val deadline = android.os.SystemClock.elapsedRealtime() + 75_000
            for (card in cards) {
                val remaining = deadline - android.os.SystemClock.elapsedRealtime()
                if (remaining <= 0) break
                val sample = when (card) {
                    is Movie -> movie(card, name)
                    is TvShow -> show(card, name)
                    else -> continue
                }
                var playable = false
                try { withTimeoutOrNull(minOf(45_000, remaining)) {
                    val details = loadProviderDetails(sample)
                    val episode = details.firstEpisode() ?: return@withTimeoutOrNull
                    for (language in listOf("VF", "VOSTFR")) {
                        resolveSources(details, episode, language, refresh = true, onPartial = { sources ->
                            if (sources.any { it.provider == name && it.reachable && it.language == language }) playable = true
                        })
                        if (playable) break
                    }
                } } catch (e: CancellationException) { throw e
                } catch (e: Exception) { failure = e.message.orEmpty() }
                if (playable) {
                    if (health[name]?.startsWith("✓ Lecture") != true) health[name] = "○ Média accessible · lecture et décodage à confirmer"
                    return@withContext true
                }
            }
            health[name] = if (protectedAccess(failure) || health[name]?.contains("Validation du site requise") == true) "⚠ Validation du site requise · ouvrez « Accès »"
                else "⚠ Aucun lecteur utilisable trouvé · essayez un autre programme"
        } catch (e: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            health[name] = "⚠ Site trop lent · réessayez plus tard"
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            health[name] = sourceFailureStatus(e.message)
        } finally {
            if (health[name] == "Vérification du média…") health[name] = "○ Test interrompu · relancez la vérification"
        }
        false
    }

    private suspend fun checkExternalSource(source: ExternalSource) {
        health[source.name] = "Vérification…"
        if (com.streamflixreborn.streamflix.utils.SourceAddresses.pending(source.name) != null) {
            health[source.name] = "⚠ Nouvelle adresse trouvée · validez-la dans « Accès »"
            return
        }
        try {
            withTimeout(12_000) {
                val connection = (URL(source.url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 2_000
                    readTimeout = 3_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "TvSama/1.0 AndroidTV")
                }
                try {
                    check(connection.responseCode in 200..399) { "HTTP ${connection.responseCode}" }
                } finally { connection.disconnect() }
            }
            health[source.name] = if (source.name == "Vidrift") "○ Site officiel accessible · lecture à confirmer dans le lecteur intégré" else "○ Site web uniquement · pas de lecteur intégré"
        } catch (e: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            health[source.name] = "⚠ Site trop lent · réessayez plus tard"
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            val message = e.message?.take(100) ?: "Site indisponible"
            health[source.name] = sourceFailureStatus(message)
        } finally {
            if (health[source.name] == "Vérification…") health[source.name] = "○ Test interrompu · relancez la vérification"
        }
    }

    fun rememberSources(anime: Anime, episode: Episode?, language: String, sources: List<VideoSource>) {
        val selected = episode ?: anime.firstEpisode() ?: return
        val key = "${identity(anime)}|${selected.seasonNumber}|${selected.number}|${selected.id}|$language"
        if (sources.isNotEmpty()) {
            evict(streamCache, 128)
            streamCache[key] = System.currentTimeMillis() to sources
        }
        sources.filter { it.reachable }.forEach { if (health[it.provider]?.startsWith("✓") != true) health[it.provider] = "○ Vidéo trouvée · lecture à confirmer en la lançant" }
    }

    private suspend fun serversFor(provider: Provider, media: Anime, episode: Episode, type: Video.Type, refresh: Boolean = false): List<Video.Server> {
        val key = "${provider.name}|${media.id}|${episode.id}"
        if (!refresh) serverCache[key]?.takeIf { System.currentTimeMillis() - it.first < 60_000 }?.let { return it.second }
        return provider.getServers(if (media.tag == "Film") media.id else episode.id, type).also { servers ->
            if (servers.isNotEmpty()) {
                evict(serverCache, 64)
                serverCache[key] = System.currentTimeMillis() to servers
            }
        }
    }

    suspend fun prefetchServers(anime: Anime, episode: Episode?) = withContext(Dispatchers.IO) {
        val selected = episode ?: anime.firstEpisode() ?: return@withContext
        interleaveReferences(anime.references.ifEmpty { listOf(MediaReference(anime.provider, anime.id, anime.tag)) })
            .distinctBy { it.provider }.take(2).forEach { ref ->
                if (!isProviderEnabled(ref.provider)) return@forEach
                try { withTimeout(5_000) {
                    val provider = owner(ref.provider)
                    val media = loadProviderDetails(anime.copy(id = ref.id, provider = ref.provider, tag = ref.tag, references = listOf(ref)))
                    val candidate = media.episodes.firstOrNull { it.seasonNumber == selected.seasonNumber && it.number == selected.number }
                        ?: return@withTimeout
                    val type = if (media.tag == "Film") Video.Type.Movie(media.id, media.title, media.year?.toString().orEmpty(), media.poster, media.imdbId)
                        else Video.Type.Episode(candidate.id, candidate.number, candidate.title, candidate.poster, candidate.description,
                            Video.Type.Episode.TvShow(media.id, media.title, media.poster, media.banner, media.year?.toString(), media.imdbId),
                            Video.Type.Episode.Season(candidate.seasonNumber, "Saison ${candidate.seasonNumber}"))
                    serversFor(provider, media, candidate, type)
                } } catch (e: TimeoutCancellationException) { currentCoroutineContext().ensureActive() }
                catch (e: CancellationException) { throw e } catch (_: Exception) { }
            }
    }

    suspend fun resolveSources(anime: Anime, episode: Episode? = null, language: String = "VF", refresh: Boolean = false,
        onPartial: (suspend (List<VideoSource>) -> Unit)? = null): List<VideoSource> = withContext(Dispatchers.IO) {
        if (anime.episodes.isEmpty()) {
            val loaded = loadDetails(anime)
            if (loaded.episodes.isEmpty()) return@withContext emptyList()
            return@withContext resolveSources(loaded, episode, language, refresh, onPartial)
        }
        val selected = episode ?: anime.firstEpisode() ?: return@withContext emptyList()
        val cacheKey = "${identity(anime)}|${selected.seasonNumber}|${selected.number}|${selected.id}|$language"
        if (!refresh) streamCache[cacheKey]?.takeIf { System.currentTimeMillis() - it.first < 120_000 }?.let { return@withContext rankSources(it.second, language) }
        val enrichedAnime = if (anime.tmdbId == null && anime.tag in listOf("Film", "Série") && !anime.isLiveContent()) {
            try { withTimeoutOrNull(4_000) { enrichWithTmdb(anime) } ?: anime } catch (_: Exception) { anime }
        } else anime
        val animation = enrichedAnime.tag == "Série" && (enrichedAnime.genres.any { it.contains("animation", true) } ||
            (enrichedAnime.references + MediaReference(enrichedAnime.provider, enrichedAnime.id, enrichedAnime.tag)).any { it.provider.contains("anime", true) })
        val aniListLookup = if (animation && isProviderEnabled("Vidrift") && language in listOf("VF", "VOSTFR"))
            async { findAniListTitle(enrichedAnime) } else null
        val known = enrichedAnime.references.ifEmpty { listOf(MediaReference(enrichedAnime.provider, enrichedAnime.id, enrichedAnime.tag)) }
        // Old favourites and provider-specific cards also need newly added language sources.
        val discovered = if (language in listOf("VF", "VOSTFR") && anime.tag == "Série" &&
            known.none { it.provider == "Animes-Sama" } && isProviderEnabled("Animes-Sama")) {
            attempt("Animes-Sama", emptyList<MediaReference>(), 6_000) {
                com.streamflixreborn.streamflix.providers.AnimesSamaProvider.search(anime.title).filterIsInstance<TvShow>()
                    .filter { CatalogIdentity.title(it.title) == CatalogIdentity.title(anime.title) }
                    .map { MediaReference("Animes-Sama", it.id, "Série") }
            }
        } else emptyList()
        val refs = interleaveReferences(known + discovered)
        val partialLock = Mutex()
        val available = mutableListOf<VideoSource>()
        val sources = (withTimeoutOrNull(45_000L) {
            refs.filter { isProviderEnabled(it.provider) }.map { ref -> async {
            attempt(ref.provider, emptyList<VideoSource>(), 35_000) {
                val p = owner(ref.provider)
                val matching = detailRequests.withPermit {
                    loadProviderDetails(anime.copy(id = ref.id, provider = ref.provider,
                        tag = ref.tag, episodes = emptyList(), references = listOf(ref)))
                }
                val candidates = episodeCandidates(matching.episodes, selected, language)
                if (candidates.isEmpty()) return@attempt emptyList()
                val collected = mutableListOf<VideoSource>()
                for (ep in candidates) {
                val type: Video.Type = if (matching.tag == "Film") Video.Type.Movie(matching.id, matching.title, matching.year?.toString().orEmpty(), matching.poster, matching.imdbId)
                    else Video.Type.Episode(ep.id, ep.number, ep.title, ep.poster, ep.description,
                        Video.Type.Episode.TvShow(matching.id, matching.title, matching.poster, matching.banner, matching.year?.toString(), matching.imdbId),
                        Video.Type.Episode.Season(ep.seasonNumber, "Saison ${ep.seasonNumber}"))
                val servers = requests.withPermit { serversFor(p, matching, ep, type, refresh) }
                    .sortedByDescending { server -> languageHint(server.name) == language }
                // A published player is only a capture input. It is never exposed as a
                // playable source: the list may contain native HLS/DASH/MP4 only.
                val providerExtractors = Semaphore(if (refs.count { isProviderEnabled(it.provider) } == 1) 4 else 2)
                val extracted = coroutineScope { servers.map { server -> async {
                    providerExtractors.withPermit { extractors.withPermit {
                        try {
                            withTimeout(35_000) {
                                val label = "${server.name} ${ep.language.takeUnless { it == "UNKNOWN" }.orEmpty()}"
                                val serverLanguage = languageHint(server.name)
                                if (serverLanguage != null && serverLanguage != language) return@withTimeout null
                                val video = withContext(com.streamflixreborn.streamflix.utils.ExtractionProvider(p)) {
                                    // Coflix resolves its API and then the original host, each with its own HTTP budget.
                                    withTimeout(if (p.name == "Coflix") 24_000 else 12_000) { p.getVideo(server) }
                                }
                                val lang = (serverLanguage ?: detectLanguage(label, video, p)).let { known ->
                                    if (known != "UNKNOWN") known else languageHint(runCatching { java.net.URI(matching.id.substringBefore('#')).path.orEmpty().substringAfterLast('/') }.getOrDefault("")) ?: "UNKNOWN"
                                }
                                if (!acceptsMediaLanguage(language, lang)) return@withTimeout null
                                if (!video.source.startsWith("http")) return@withTimeout null
                                val raw = VideoSource(server.name.ifBlank { p.name }, video.source, lang, declaredQuality(server.name), p.name,
                                    video.headers.orEmpty(), video.subtitles.filter { french(it.label) }.map {
                                        VideoSubtitle(it.file, "fr", it.label, if (it.file.substringBefore('?').endsWith(".srt")) "application/x-subrip" else "text/vtt")
                                    }, video.type, serverId = server.id)
                                // Une source VOSTFR sans sous-titre français est inutilisable : on l’écarte plutôt que
                                // de proposer une VO incompréhensible. Les sous-titres incrustés ne peuvent pas être
                                // détectés ici. Un manifeste HLS qui déclare une piste française est toutefois
                                // exploitable nativement : on garde alors la source.
                                val hls = raw.mimeType?.contains("mpegurl", true) == true ||
                                    raw.url.substringBefore('?').endsWith(".m3u8", true)
                                if (lang == "VOSTFR" && raw.subtitles.isEmpty() && !(hls && hlsDeclaresFrenchSubtitles(raw)))
                                    return@withTimeout null
                                probe(raw, matching.isLiveContent()).takeIf { it.reachable }?.also { usable ->
                                    partialLock.withLock {
                                        available += usable
                                        val partial = rankSources(available.distinctBy { "${it.url}|${it.language}" }, language)
                                        if (health[p.name]?.startsWith("✓") != true) health[p.name] = "○ Vidéo trouvée · lecture à confirmer en la lançant"
                                        onPartial?.invoke(partial)
                                    }
                                }
                            }
                        } catch (e: TimeoutCancellationException) { currentCoroutineContext().ensureActive(); null }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) {
                            if (protectedAccess(e.message) && health[p.name]?.startsWith("✓") != true)
                                health[p.name] = "⚠ Validation du site requise · ouvrez « Accès »"
                            null
                        }
                    } }
                } }.awaitAll().filterNotNull() }
                collected += extracted
                if (extracted.isEmpty()) {
                    // A published player is used only as a hidden capture input. If no native
                    // media request is captured, the provider contributes no source.
                    val activity = com.streamflixreborn.streamflix.utils.SourceWebSession.currentActivity()
                    if (activity != null) for (server in servers.filter { it.src.startsWith("https://") && !it.src.contains("/api/") }.take(2)) {
                        // Only attempt a page whose language is explicitly published. The
                        // episode's language alone does not guarantee the embedded audio.
                        val lang = languageHint(server.name) ?: continue
                        if (!acceptsMediaLanguage(language, lang)) continue
                        try {
                            val candidate = withTimeout(8_000) { resolveLiveWebPlayer(activity, server.src, p.baseUrl) }
                                .copy(name = server.name, provider = p.name, language = lang, serverId = server.id)
                            val validated = probe(candidate, matching.isLiveContent()).takeIf { it.reachable } ?: continue
                            partialLock.withLock {
                                available += validated
                                val partial = rankSources(available.distinctBy { "${it.url}|${it.language}" }, language)
                                if (health[p.name]?.startsWith("✓") != true) health[p.name] = "○ Vidéo trouvée · lecture à confirmer en la lançant"
                                onPartial?.invoke(partial)
                            }
                            collected += validated
                            break
                        } catch (e: TimeoutCancellationException) { currentCoroutineContext().ensureActive() }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { }
                    }
                }
                }
                collected
            }
        } }.awaitAll().flatten()
        } ?: emptyList()) + partialLock.withLock { available.toList() }
        val animeNative = if (aniListLookup != null) {
            try { withTimeout(6_000) { aniListLookup.await() }?.let { resolveVidriftNativeSources(anime, selected, language, it) }.orEmpty() }
            catch (error: TimeoutCancellationException) { currentCoroutineContext().ensureActive(); emptyList() }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { emptyList() }
        } else emptyList()
        val vidNative = if (isProviderEnabled("Vidrift")) {
            val candidates = resolveVidriftNativeSources(enrichedAnime, selected, language) + animeNative
            // Ne proposer une source Vidrift que si la piste existe réellement :
            // résolvez l'API Vidrift pour confirmer le flux (URL HLS, audio, sous-titres)
            // avant de l'afficher dans la liste. Une source qui ne résout pas est écartée.
            coroutineScope {
                candidates.map { candidate ->
                    async {
                        withTimeoutOrNull(10_000) { resolveVidriftStream(candidate) }
                    }
                }.awaitAll().filterNotNull()
            }
        } else emptyList()
        // Never relabel an unconfirmed reader: a VO page hidden behind a VF label is exactly
        // what makes the wrong audio start. Only explicitly matching languages survive.
        val verified = rankSources((sources + vidNative).distinctBy { "${it.url}|${it.headers}|${it.language}" }, language)
            .filter { it.language == language }
        if (verified.none { it.reachable && it.mimeType != VIDRIFT_MIME_TYPE } && !refresh) {
            val previousAddresses = refs.associate { it.provider to sourceUrl(it.provider) }
            try { withTimeout(8_000) { refreshDirectory(force = false) } }
            catch (e: TimeoutCancellationException) { currentCoroutineContext().ensureActive() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { }
            if (previousAddresses.any { (name, address) -> sourceUrl(name) != address })
                return@withContext resolveSources(anime, selected, language, refresh = true, onPartial = onPartial)
        }
        streamCache[cacheKey] = System.currentTimeMillis() to verified
        verified
    }
    suspend fun getStreamsForEpisode(anime: Anime, episode: Episode, language: String = "VF") = resolveSources(anime, episode, language)
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
    private fun detectLanguage(label: String, video: Video, provider: Provider): String =
        if (provider is IptvProvider && provider.language == "fr") "VF"
        else mediaLanguage(label, video.subtitles.map { it.label })
    private fun qualityScore(q: String) = when(q) { "4K" -> 4; "1080p" -> 3; "720p" -> 2; "480p" -> 1; else -> 0 }
    /** Native confirmed readers first, then Vidrift, then uqload. */
    private fun reliabilityRank(source: VideoSource): Int = when {
        source.mimeType == VIDRIFT_MIME_TYPE -> 2
        source.name.contains("uqload", true) || source.url.contains("uqload", true) -> 1
        else -> 0
    }
    /**
     * Order: confirmed status -> requested version -> reliability -> quality -> latency.
     * Providers are interleaved so one host never fills the whole list, then the
     * status/reliability order is re-applied; the sort is stable, so interleaving is kept
     * inside equal ranks.
     */
    internal fun rankSources(sources: List<VideoSource>, requestedLanguage: String? = null) =
        sources.filterNot { it.url in rejectedShortMedia }.sortedWith(
        compareBy<VideoSource> { !it.reachable }
            .thenBy { requestedLanguage != null && it.language != requestedLanguage }
            .thenBy { it.language == "UNKNOWN" }
            .thenBy { reliabilityRank(it) }
            .thenByDescending { qualityScore(it.quality) }
            .thenBy { if (it.latencyMs < 0) Long.MAX_VALUE else it.latencyMs }
            .thenBy { it.provider }).groupBy { it.language == "UNKNOWN" }.values.flatMap { languageGroup ->
        val providers = languageGroup.groupBy { it.provider }.values.toList()
        (0 until (providers.maxOfOrNull { it.size } ?: 0)).flatMap { index -> providers.mapNotNull { it.getOrNull(index) } }
    }.sortedWith(compareBy<VideoSource> { !it.reachable }.thenBy { reliabilityRank(it) })
    private suspend fun probe(source: VideoSource, live: Boolean = false): VideoSource = withContext(Dispatchers.IO) {
        if (source.url in rejectedShortMedia) return@withContext source.copy(latencyMs = -1, reachable = false)
        val started = System.nanoTime()
        val checked = validateLiveSource(source, minimumVodDurationMs = if (live) 0 else 90_000, verifyLanguage = !live)
        if (checked == null || source.url in rejectedShortMedia) source.copy(latencyMs = -1, reachable = false)
        else checked.copy(latencyMs = ((System.nanoTime() - started) / 1_000_000L).coerceAtLeast(1L))
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
    private fun releaseDate(date: Calendar?) = date?.let {
        java.time.LocalDate.of(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH)).toString()
    }
    private fun tmdbId(provider: String, id: String): Int? = when (provider) {
        "WarFlix" -> id.trimEnd('/').substringAfterLast('/').toIntOrNull()
        // Movix is backed by TMDb and publishes the TMDb id directly.
        "Movix" -> id.trimEnd('/').substringAfterLast('/').toIntOrNull()
        else -> null
    }
    private fun movie(m: Movie, provider: String) = Anime(m.title, "Film", listOf(Episode("Film", m.overview.orEmpty(), emptyList(), m.id, 1, 1, artwork(m.poster, provider))),
        m.id, artwork(m.poster, provider), TvSamaAnimeProvider.cleanSynopsis(m.overview), m.released?.get(Calendar.YEAR), m.genres.map { it.name },
        provider, artwork(m.banner, provider), listOf(MediaReference(provider, m.id, "Film")), m.imdbId,
        firstAired = releaseDate(m.released), rating = m.rating ?: 0.0,
        tmdbId = tmdbId(provider, m.id),
        originalLanguage = if (provider == "WarFlix") com.streamflixreborn.streamflix.providers.WarFlixProvider.originalLanguage(m.id) else null,
        popularity = if (provider == "WarFlix") com.streamflixreborn.streamflix.providers.WarFlixProvider.signals(m.id)?.popularity ?: 0.0 else 0.0,
        voteCount = if (provider == "WarFlix") com.streamflixreborn.streamflix.providers.WarFlixProvider.signals(m.id)?.votes ?: 0 else 0)
    private fun show(t: TvShow, provider: String) = Anime(t.title, "Série", emptyList(), t.id, artwork(t.poster, provider), TvSamaAnimeProvider.cleanSynopsis(t.overview),
        t.released?.get(Calendar.YEAR), t.genres.map { it.name }, provider, artwork(t.banner, provider), listOf(MediaReference(provider, t.id, "Série")), t.imdbId,
        firstAired = releaseDate(t.released), rating = t.rating ?: 0.0,
        tmdbId = tmdbId(provider, t.id),
        originalLanguage = if (provider == "WarFlix") com.streamflixreborn.streamflix.providers.WarFlixProvider.originalLanguage(t.id) else null,
        popularity = if (provider == "WarFlix") com.streamflixreborn.streamflix.providers.WarFlixProvider.signals(t.id)?.popularity ?: 0.0 else 0.0,
        voteCount = if (provider == "WarFlix") com.streamflixreborn.streamflix.providers.WarFlixProvider.signals(t.id)?.votes ?: 0 else 0,
        isLive = owner(provider) is IptvProvider)
}

data class ExternalSource(val name: String, val url: String, val limitation: String = "Lien externe · pas de catalogue intégré")
data class MediaReference(val provider: String, val id: String, val tag: String)
internal fun episodeCandidates(episodes: List<Episode>, selected: Episode, language: String): List<Episode> =
    episodes.filter { it.number == selected.number && it.seasonNumber == selected.seasonNumber }
        .filter { it.language == language || it.language == "UNKNOWN" }
        .distinctBy { it.id }
        .sortedWith(compareBy<Episode> {
            it.language != language
        }.thenBy { it.language == "UNKNOWN" })
        .take(4)

internal fun interleaveReferences(references: List<MediaReference>): List<MediaReference> {
    val grouped = references.distinct().groupBy { it.provider }.values.sortedBy { it.first().provider == "WarFlix" }
    return (0 until (grouped.maxOfOrNull { it.size } ?: 0)).flatMap { index -> grouped.mapNotNull { it.getOrNull(index) } }
}
internal fun sourceFailureStatus(message: String?): String = when {
    Regex("(?i)captcha|cloudflare|validation|à valider|accès protégé|antibot").containsMatchIn(message.orEmpty()) ->
        "⚠ Validation du site requise · ouvrez « Accès »"
    Regex("(?i)\\b403\\b|\\b401\\b").containsMatchIn(message.orEmpty()) ->
        "⚠ Accès refusé par le site · essayez « Accès »"
    Regex("(?i)\\b404\\b").containsMatchIn(message.orEmpty()) ->
        "⚠ Page introuvable · actualisez les adresses et catalogues"
    Regex("(?i)\\b429\\b").containsMatchIn(message.orEmpty()) ->
        "⚠ Trop de demandes au site · réessayez plus tard"
    message.orEmpty().contains("Catalogue vide", true) -> "⚠ Catalogue vide · actualisez ou réessayez plus tard"
    else -> "⚠ Connexion au site impossible · réessayez plus tard"
}
internal fun additionalProviderReferences(previous: Anime, current: Anime): List<MediaReference> {
    val known = previous.references.map { it.provider }.toSet() + previous.provider
    return current.references.filter { it.provider !in known }
}
internal fun relatedCatalogueMatches(target: Anime, cards: List<Anime>): List<Anime> {
    val titles = (target.aliases + target.title).map(CatalogIdentity::title).filter(String::isNotBlank).toSet()
    val references = (target.references + MediaReference(target.provider, target.id, target.tag)).toSet()
    fun sameIdentity(card: Anime) = target.imdbId != null && target.imdbId == card.imdbId ||
        (card.references + MediaReference(card.provider, card.id, card.tag)).any { it in references }
    val targetYear = CatalogIdentity.effectiveYear(target)
    fun yearAccepts(card: Anime): Boolean {
        if (sameIdentity(card)) return true
        val cardYear = CatalogIdentity.effectiveYear(card)
        return if (targetYear != null) cardYear == null || cardYear == targetYear
        // Never attach an explicitly dated edition to an unknown one: that is how the
        // 1999 and 2011 Hunter x Hunter catalogues end up in the same card.
        else CatalogIdentity.titleYear(card.title) == null
    }
    val matched = cards.filter { card ->
        if (card.tag == "Direct" || target.tag == "Direct") return@filter false
        val nameMatch = (card.aliases + card.title).any { CatalogIdentity.title(it) in titles }
        val filmish = card.tag != target.tag &&
            (CatalogIdentity.isMovieTitle(card.title) || CatalogIdentity.isMovieTitle(target.title))
        (card.tag == target.tag || filmish) && yearAccepts(card) &&
            (sameIdentity(card) || (target.imdbId == null || card.imdbId == null || target.imdbId == card.imdbId) && nameMatch)
    }
    return (if (targetYear == null && matched.mapNotNull(::effectiveYearOf).distinct().size > 1)
        matched.filter(::sameIdentity) else matched).distinctBy { "${it.provider}|${it.id}|${it.tag}" }
}

private fun effectiveYearOf(card: Anime) = CatalogIdentity.effectiveYear(card)
data class Anime(val title: String, val tag: String, val episodes: List<Episode>, val id: String = "", val poster: String = "",
    val description: String = "", val year: Int? = null, val genres: List<String> = emptyList(), val provider: String = "",
    val banner: String = "", val references: List<MediaReference> = emptyList(), val imdbId: String? = null,
    val firstAired: String? = null, val lastAired: String? = null, val seriesStatus: String? = null, val metadataUrl: String? = null,
    val aliases: List<String> = emptyList(), val popularity: Double = 0.0, val rating: Double = 0.0, val voteCount: Int = 0,
    val isLive: Boolean = false, val tmdbId: Int? = null, val originalLanguage: String? = null,
    /** Language-tagged titles from TMDB/AniList: "fr", "en", "ja", "original", "romaji". */
    val localizedTitles: Map<String, String> = emptyMap())
data class Episode(val title: String, val description: String, val sources: List<VideoSource>, val id: String = "",
    val number: Int = 1, val seasonNumber: Int = 1, val poster: String = "", val language: String = "UNKNOWN", val introSeason: Int? = null, val introNumber: Int? = null)
internal fun Anime.firstEpisode(): Episode? = episodes.firstOrNull { it.seasonNumber == 1 && it.number == 1 }
    ?: episodes.firstOrNull { it.seasonNumber > 0 } ?: episodes.firstOrNull()
data class VideoSubtitle(val url: String, val language: String = "fr", val label: String = "Français", val mimeType: String = "text/vtt")
data class VideoSource(val name: String, val url: String, val language: String, val quality: String, val provider: String,
    val headers: Map<String, String> = emptyMap(), val subtitles: List<VideoSubtitle> = emptyList(), val mimeType: String? = null,
    val latencyMs: Long = -1, val reachable: Boolean = true, val serverId: String = "",
    val originalLanguage: String? = null)

internal fun Anime.isLiveContent() = isLive || tag == "Direct" || provider == "Pluto TV FR"
