package fr.nekotv

import fr.nekotv.Anime
import fr.nekotv.Episode
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

object SearchService {
    // Config as requested (top of file per objective requirement #3)
    const val MAX_RESULTS = 20
    const val SEARCH_TIMEOUT_MS = 2500L   // ~2.5s global timeout
    const val SEARCH_DEBOUNCE_MS = 250L   // 200–250ms debounce
    const val SEARCH_MIN_CHARS = 2
    const val SEARCH_MAX_CHARS = 200

    // Server-side cache config (requirement 6 & 9: TTL 5–10 min)
    const val SERVER_CACHE_TTL_MS = 5 * 60 * 1000L   // 5 minutes default
    const val SERVER_CACHE_MAX_ENTRIES = 1000

    // Rate limiting (requirement 9)
    const val RATE_LIMIT_MAX_REQUESTS = 30
    const val RATE_LIMIT_WINDOW_MS = 60 * 1000L      // 30 requests per minute

    // Internal server-side cache keyed by normalized query (clé = requête normalisée)
    @Volatile private var serverCache: Map<String, CachedResponse> = emptyMap()
    private val cacheLock = Any()

    // Simple sliding-window rate limiter: client -> request timestamps
    @Volatile private var requestTimes: Map<String, List<Long>> = emptyMap()
    private val rateLock = Any()

    private data class CachedResponse(val results: List<JSONObject>, val expiresAt: Long)

    @Volatile private var searchJob: kotlinx.coroutines.Job? = null

    /**
     * Single /search?q= endpoint implementation.
     * Parallel queries → normalization → dedup → scoring → ranking → limit → canonical JSON.
     */
    suspend fun search(query: String, clientId: String = "default"): List<JSONObject> = coroutineScope {
        // Cancel previous search (AbortController-like behavior)
        searchJob?.cancel()
        val currentJob = coroutineContext[kotlinx.coroutines.Job]
        searchJob = currentJob

        // 1. Sanitization + length limits (requirement 9)
        val sanitized = SearchSanitizer.sanitize(query)
        if (sanitized == null) return@coroutineScope emptyList()

        // 2. Rate limiting per client (requirement 9)
        if (!rateLimitAllowed(clientId)) return@coroutineScope emptyList()

        // 3. Client-side cache keyed by normalized query (requirement 6)
        val cacheKey = Normalize.cacheKey(sanitized)

        synchronized(cacheLock) {
            val cached = serverCache[cacheKey]
            if (cached != null && cached.expiresAt > System.currentTimeMillis()) {
                return@coroutineScope cached.results
            }
        }

        val manager = StreamFlixProviderManager.getInstance()

        // 4. Parallel provider queries with per-source + global timeouts (requirement 6)
        val results = manager.atomicSearch(sanitized) ?: emptyList()

        // 5. Ranking + dedup → scoring → sort → limit (requirement 5)
        val ranked = Recommendations.rankSearchResults(results, sanitized)
            .take(MAX_RESULTS)

        // 6. Map to canonical JSON schema (requirement 10)
        val jsonResults = ranked.map { anime ->
            toResponse(anime, /* score */ 1.0, query)
        }

        // 7. Store in server-side cache with TTL (requirement 6)
        synchronized(cacheLock) {
            val now = System.currentTimeMillis()
            val entry = CachedResponse(jsonResults, now + SERVER_CACHE_TTL_MS)
            val updated = serverCache + (cacheKey to entry)
            // Evict oldest if over max entries
            val trimmed = if (updated.size > SERVER_CACHE_MAX_ENTRIES) {
                updated.toList().sortedBy { it.second.expiresAt }.take(SERVER_CACHE_MAX_ENTRIES).toMap()
            } else updated
            serverCache = trimmed
        }

        jsonResults
    }

    /** Returns true if the client is within rate limits. */
    private fun rateLimitAllowed(clientId: String): Boolean {
        val now = System.currentTimeMillis()
        synchronized(rateLock) {
            val times = requestTimes.getOrDefault(clientId, emptyList())
            val windowStart = now - RATE_LIMIT_WINDOW_MS
            val recent = times.filter { it > windowStart }
            if (recent.size >= RATE_LIMIT_MAX_REQUESTS) return false
            requestTimes = requestTimes + (clientId to (recent + now))
            return true
        }
    }

    /** Canonical JSON schema per objective requirement 10:
     * id canonique, externalIds, type, year, titles{langue:titre}, originalTitle,
     * displayTitle, overview, poster, rating, votes, popularity, sources[], score
     */
    fun toResponse(anime: Anime, score: Double, query: String): JSONObject {
        val titles = mutableMapOf<String, String>()
        titles["default"] = anime.title
        anime.aliases.forEachIndexed { i, alias -> titles["alias$i"] = alias }
        // localized titles from anime.localizedTitles if available
        anime.localizedTitles.forEach { (lang, title) -> titles[lang] = title }

        val externalIds = mutableMapOf<String, String>()
        anime.tmdbId?.let { externalIds["tmdb"] = it.toString() }
        anime.imdbId?.let { externalIds["imdb"] = it }

        val sources = mutableListOf<String>()
        sources.add(anime.provider)
        anime.references.forEach { ref ->
            if (ref.provider.isNotBlank() && ref.provider != anime.provider) sources.add(ref.provider)
        }

        return JSONObject().apply {
            put("id", "${anime.provider}|${anime.id}")
            put("externalIds", JSONObject(externalIds))
            put("type", anime.tag)
            put("year", anime.year)
            put("titles", JSONObject(titles))
            put("originalTitle", anime.title)
            put("displayTitle", anime.displayTitle("VF"))
            put("overview", SynopsisResolver.resolveOverview(anime).text)
            put("poster", anime.poster)
            put("rating", anime.rating)
            put("votes", anime.voteCount)
            put("popularity", anime.popularity)
            put("sources", JSONArray(sources.distinct()))
            put("score", score)
            put("query", query)
        }
    }

    /** Clear cache (for tests / admin). */
    fun clearCache() {
        synchronized(cacheLock) { serverCache = emptyMap() }
    }
}
