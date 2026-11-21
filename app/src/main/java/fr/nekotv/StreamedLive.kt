package fr.nekotv

import com.streamflixreborn.streamflix.providers.DirectEvent
import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.utils.NetworkClient
import com.streamflixreborn.streamflix.utils.SourceAddresses
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** The directory supplies candidates; only a valid events API can replace the saved address. */
internal object StreamedLive {
    private const val DEFAULT = "https://streamed.pk/"
    private val client by lazy { NetworkClient.default.newBuilder().apply {
        interceptors().remove(SourceAddresses.interceptor)
    }.callTimeout(5, TimeUnit.SECONDS).build() }
    private val lock = Mutex()
    private var checkedAt = 0L
    private var mirrors = listOf(DEFAULT, "https://streamed.st/")
    private val eventSources = ConcurrentHashMap<String, List<Pair<String, String>>>()
    var status: String = "Pas encore testé"
        private set
    private fun json(address: String): JSONArray = client.newCall(Request.Builder().url(address).build()).execute().use {
        check(it.isSuccessful) { "Streamed : HTTP ${it.code}" }
        val source = it.body?.source() ?: error("Streamed : réponse vide")
        source.request(2_000_001)
        check(source.buffer.size <= 2_000_000) { "Streamed : réponse trop volumineuse" }
        JSONArray(source.buffer.readUtf8())
    }
    suspend fun events(force: Boolean = false): List<DirectEvent> = withContext(Dispatchers.IO) { lock.withLock {
        if (force || System.currentTimeMillis() - checkedAt > 6 * 60 * 60_000) {
            checkedAt = System.currentTimeMillis()
            val primary = try { SourceDirectory.loadFrench().filter { it.slug == "streamed" && it.status in setOf("active", "redirected") }.map { it.url } }
                catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
            val wiki = try { SourceDirectory.liveWiki().map { it.url } }
                catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
            mirrors = (primary + wiki + mirrors).distinct().take(6)
        }
        var failure: Exception? = null
        for (base in (listOf(SourceAddresses.current("Streamed", DEFAULT)) + mirrors).distinct()) {
            try {
                val root = base.toHttpUrl().newBuilder().encodedPath("/").query(null).fragment(null).build()
                val data = json(root.resolve("api/matches/all").toString())
                val live = json(root.resolve("api/matches/live").toString())
                val active = (0 until live.length()).map { live.getJSONObject(it).getString("id") }.toSet()
                val events = parseEvents(data, root.toString(), active)
                check(events.isNotEmpty()) { "Streamed : calendrier vide ou format non reconnu" }
                eventSources.clear()
                for (i in 0 until data.length()) {
                    val item = data.getJSONObject(i)
                    val sources = item.optJSONArray("sources") ?: continue
                    eventSources[item.optString("id")] = (0 until sources.length()).mapNotNull { index ->
                        val source = sources.getJSONObject(index)
                        val name = source.optString("source"); val id = source.optString("id")
                        if (name.isBlank() || id.isBlank()) null else name to id
                    }
                }
                SourceAddresses.update("Streamed", SourceAddresses.current("Streamed", DEFAULT), root.toString())
                status = "Catalogue accessible · ${events.size} événements · ${root.host} · lecture à tester"
                return@withLock events
            } catch (e: CancellationException) { throw e } catch (e: Exception) { failure = e }
        }
        status = "Calendrier indisponible après essai des adresses de secours"
        throw IllegalStateException(status, failure)
    } }
    internal fun parseEvents(data: JSONArray, base: String, active: Set<String>): List<DirectEvent> =
        (0 until data.length()).mapNotNull { index ->
            val item = data.getJSONObject(index)
            val id = item.optString("id"); val title = item.optString("title"); val date = item.optLong("date")
            if (id.isBlank() || title.isBlank() || date <= 0 || (item.optJSONArray("sources")?.length() ?: 0) == 0) null
            else DirectEvent("streamed:$id", title, "Streamed · ${item.optString("category")}", date, id in active,
                base.toHttpUrl().newBuilder().addPathSegment("watch").addPathSegment(id).build().toString())
        }.distinctBy { it.id }.sortedBy { it.startsAt }

    suspend fun servers(eventUrl: String): List<Video.Server> = withContext(Dispatchers.IO) {
        val id = eventUrl.toHttpUrl().pathSegments.last()
        if (eventSources[id] == null) events()
        val root = SourceAddresses.current("Streamed", DEFAULT).toHttpUrl()
        val servers = mutableListOf<Video.Server>()
        for ((source, key) in eventSources[id].orEmpty().take(5)) {
            try {
                val url = root.newBuilder().addPathSegment("api").addPathSegment("stream").addPathSegment(source).addPathSegment(key).build()
                servers += parseServers(json(url.toString()))
            } catch (e: CancellationException) { throw e } catch (_: Exception) { /* Another published provider may still answer. */ }
        }
        servers.distinctBy { it.src }.sortedBy { liveLanguagePriority(it.name) }
    }
    internal fun parseServers(data: JSONArray): List<Video.Server> = (0 until data.length()).mapNotNull { index ->
        val item = data.getJSONObject(index)
        val url = safeDirectoryMediaUrl(item.optString("embedUrl")) ?: return@mapNotNull null
        Video.Server(url, "${item.optString("source")} · ${item.optString("language")} · ${if (item.optBoolean("hd")) "HD" else "SD"}", url)
    }
    private fun safeDirectoryMediaUrl(value: String): String? = runCatching { value.toHttpUrl() }.getOrNull()
        ?.takeIf { it.isHttps && it.username.isEmpty() && it.password.isEmpty() }?.toString()
    fun owns(url: String) = runCatching { url.toHttpUrl().pathSegments.firstOrNull() == "watch" }.getOrDefault(false)
}

internal fun liveLanguagePriority(label: String): Int = when {
    Regex("(?i)(?:\\b(?:fr|fra|french|français|francais|vf)\\b)").containsMatchIn(label) -> 0
    Regex("(?i)\\b(?:english|eng|en|spanish|german|arabic|italian|portuguese)\\b").containsMatchIn(label) -> 2
    else -> 1
}
