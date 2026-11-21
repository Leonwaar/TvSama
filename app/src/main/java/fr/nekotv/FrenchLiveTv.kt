package fr.nekotv

import com.streamflixreborn.streamflix.providers.DirectEvent
import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.utils.NetworkClient
import com.streamflixreborn.streamflix.utils.SourceAddresses
import kotlinx.coroutines.*
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** French channels and their alternate HLS addresses, as published by TVradioZap. */
internal object FrenchLiveTv {
    const val BASE = "https://tvradiozap.eu/"
    private val feeds = ConcurrentHashMap<String, List<Video.Server>>()
    private var cached: Pair<Long, List<DirectEvent>>? = null
    private val client by lazy { NetworkClient.default.newBuilder().callTimeout(8, TimeUnit.SECONDS).build() }
    suspend fun events(force: Boolean = false): List<DirectEvent> = withContext(Dispatchers.IO) {
        cached?.takeIf { !force && System.currentTimeMillis() - it.first < 15 * 60_000 }?.let { return@withContext it.second }
        val saved = SourceAddresses.current("TVradioZap", BASE)
        val backups = try { SourceDirectory.wiki("TVradioZap").map { it.url } }
            catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
        for (address in (listOf(saved) + backups + BASE).distinct()) {
            try {
                val doc = client.newCall(Request.Builder().url(address).build()).execute().use { response ->
                    check(response.isSuccessful) { "TVradioZap : HTTP ${response.code}" }
                    val input = response.body?.source() ?: error("TVradioZap : réponse vide")
                    input.request(1_000_001)
                    check(input.buffer.size <= 1_000_000) { "TVradioZap : réponse trop volumineuse" }
                    Jsoup.parse(input.buffer.readUtf8(), response.request.url.toString())
                }
                val parsed = channels(doc)
                check(parsed.isNotEmpty()) { "TVradioZap : chaînes françaises absentes" }
                feeds.clear(); feeds.putAll(parsed.associate { it.first.id to it.second })
                val result = parsed.map { it.first }
                SourceAddresses.update("TVradioZap", saved, doc.baseUri())
                cached = System.currentTimeMillis() to result
                return@withContext result
            } catch (e: CancellationException) { throw e } catch (_: Exception) { }
        }
        cached?.second ?: error("TVradioZap : adresses indisponibles")
    }
    internal fun channels(doc: Document): List<Pair<DirectEvent, List<Video.Server>>> = doc.select("tr[data-tv][data-idx]").mapNotNull { row ->
        if ((row.attr("data-tv").toIntOrNull() ?: 0) and 1 == 0) return@mapNotNull null
        val cells = row.select("td")
        if (cells.getOrNull(4)?.text()?.trim() != "France") return@mapNotNull null
        val title = cells.getOrNull(2)?.text()?.trim().orEmpty()
        val key = row.attr("data-idx").takeIf { it.toLongOrNull() != null } ?: return@mapNotNull null
        val servers = row.select("a[target=ecoute][href]").mapNotNull { link ->
            val url = link.absUrl("href").toHttpUrlOrNull()?.takeIf { it.isHttps && it.username.isEmpty() && it.password.isEmpty() && it.encodedPath.endsWith(".m3u8", true) }
                ?.newBuilder()?.fragment(null)?.build() ?: return@mapNotNull null
            Video.Server(url.toString(), "$title · FR · ${url.host}", url.toString())
        }.distinctBy { it.src }
        if (title.isBlank() || servers.isEmpty()) null else DirectEvent("frtv:$key", title,
            "TV française · ${cells.getOrNull(3)?.text().orEmpty()}", 0, true, "${doc.baseUri().substringBefore('#')}#channel=$key") to servers
    }.distinctBy { it.first.id }
    fun isSport(event: DirectEvent) = event.competition.substringAfter("·").trim().equals("Sport", true)
    fun owns(url: String) = url.toHttpUrlOrNull()?.fragment?.startsWith("channel=") == true
    suspend fun servers(url: String): List<Video.Server> {
        val key = "frtv:${url.substringAfter("#channel=")}"
        if (feeds[key] == null) events()
        return feeds[key].orEmpty()
    }
}
