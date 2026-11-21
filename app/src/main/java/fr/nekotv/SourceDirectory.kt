package fr.nekotv

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import com.streamflixreborn.streamflix.utils.NetworkClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Discovery records are not playback adapters. Keep the stable slug to refresh rotating URLs. */
data class DirectorySource(val name: String, val slug: String, val url: String, val status: String, val origin: String = "OùStreamer", val recordUrl: String? = null) {
    val permanentUrl get() = recordUrl ?: "https://app.oustreamer.com/site/$slug"
}

object SourceDirectory {
    @Volatile var lastReport: String = "Annuaire pas encore interrogé"
        private set
    suspend fun loadFrench(): List<DirectorySource> = withContext(Dispatchers.IO) {
        fun fetch(page: Int): JSONObject = JSONObject(fetchPublic("https://app.oustreamer.com/api/sites?per_page=100&page=$page", "application/json")).also {
            check(it.optBoolean("success")) { "Réponse de l’annuaire invalide" }
        }
        val first = fetch(1)
        val last = first.optJSONObject("meta")?.optInt("last_page", 1)?.coerceIn(1, 50) ?: 1
        val slots = Semaphore(2)
        val failedPages = java.util.concurrent.atomic.AtomicInteger()
        val pages = listOf(first) + coroutineScope {
            (2..last).map { page -> async { slots.withPermit {
                ensureActive()
                try { fetch(page) } catch (e: CancellationException) { throw e }
                catch (_: Exception) { failedPages.incrementAndGet(); null }
            } } }.awaitAll().filterNotNull()
        }
        lastReport = if (failedPages.get() == 0) "Annuaire principal reçu · ${pages.size} pages"
            else "Annuaire partiel · ${failedPages.get()} pages indisponibles, dernières adresses conservées"
        pages.flatMap(::parsePrimaryPage).distinctBy { it.slug }
    }

    internal fun parsePrimaryPage(data: JSONObject): List<DirectorySource> {
        val entries = mutableListOf<DirectorySource>()
        val items = data.getJSONArray("data")
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            if (item.optString("language") !in setOf("fr", "multi") || item.optString("category") !in setOf("films", "series", "anime", "iptv", "sport")) continue
            val status = item.optString("status").let { if (it == "danger") "blocked" else it }
            val url = safeDirectoryAddress(item.optJSONObject("current_url")?.optString("url").orEmpty())
            if (url == null && status in setOf("active", "redirected")) continue
            val slug = item.optString("slug")
            if (!Regex("[a-z0-9][a-z0-9.-]{0,127}").matches(slug)) continue
            val name = item.optString("name").take(200).takeIf(String::isNotBlank) ?: continue
            entries += DirectorySource(name, slug, url.orEmpty(), status)
        }
        return entries
    }

    private val backupPages = mapOf("Wiflix" to "wiflix", "Coflix" to "coflix",
        "FrenchStream" to "french-stream", "Papadustream" to "papadustream")
    private val backupCache = ConcurrentHashMap<String, Pair<Long, List<DirectorySource>>>()
    private val wikiLock = Mutex()
    private var wikiCache: Pair<Long, List<DirectorySource>>? = null
    private var wikiRetryAfter = 0L
    private val wikiNames = mapOf("tvradiozap" to "TVradioZap", "frenchstream" to "FrenchStream", "frembed" to "Frembed", "movix" to "Movix",
        "kidraz" to "Kidraz", "myfluneo" to "MyFluneo", "flemmix" to "Wiflix", "voiranime" to "Voiranime",
        "vostfree" to "Vostfree", "franime" to "FRAnime", "animesama" to "Anime-Sama", "frenchanime" to "FrenchAnime", "animeko" to "AnimeKO")
    private val client by lazy { NetworkClient.default.newBuilder().callTimeout(12, TimeUnit.SECONDS).build() }

    private fun fetchPublic(address: String, accept: String): String = client.newCall(Request.Builder().url(address)
        .header("Accept", accept).build()).execute().use { response ->
        check(response.isSuccessful) { "Annuaire indisponible (HTTP ${response.code})" }
        val body = response.body ?: error("Réponse vide de l’annuaire")
        val input = body.source()
        input.request(1_000_001)
        check(input.buffer.size <= 1_000_000) { "Réponse trop volumineuse de l’annuaire" }
        input.buffer.readUtf8()
    }

    suspend fun fallback(name: String): List<DirectorySource> = withContext(Dispatchers.IO) {
        val slug = backupPages[name] ?: return@withContext emptyList()
        backupCache[name]?.takeIf { System.currentTimeMillis() - it.first < 10 * 60_000 }?.let { return@withContext it.second }
        val address = "https://adressesdirect.com/websites/$slug"
        val records = parseFallback(name, slug, Jsoup.parse(fetchPublic(address, "text/html"), address))
        backupCache[name] = System.currentTimeMillis() to records
        records
    }

    internal fun parseFallback(name: String, slug: String, document: Document): List<DirectorySource> =
        document.select("section").filter { it.selectFirst("h2")?.text()?.trim() == "URLs:" }
            .flatMap { it.select("a[href]") }.filter { link ->
                link.select("span").any { it.text().trim().equals("Actif", true) } && link.selectFirst("code") != null
            }.mapNotNull { link ->
                val address = safeDirectoryAddress(link.absUrl("href")) ?: return@mapNotNull null
                if (safeDirectoryAddress(link.selectFirst("code")!!.text().trim()) != address) return@mapNotNull null
                DirectorySource(name, slug, address, "active", "AdressesDirect", "https://adressesdirect.com/websites/$slug")
            }.distinctBy { it.url }.take(3)

    suspend fun wiki(name: String): List<DirectorySource> = withContext(Dispatchers.IO) {
        if (name !in wikiNames.values) return@withContext emptyList()
        wikiLock.withLock {
            check(System.currentTimeMillis() >= wikiRetryAfter) { "Wiki temporairement indisponible" }
            val records = wikiCache?.takeIf { System.currentTimeMillis() - it.first < 10 * 60_000 }?.second
                ?: try {
                    parseWiki(Jsoup.parse(fetchPublic("https://fmhy.net/non-english", "text/html"), "https://fmhy.net/non-english"))
                        .also { check(it.isNotEmpty()) { "Adresses du wiki non reconnues" }; wikiCache = System.currentTimeMillis() to it }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { wikiRetryAfter = System.currentTimeMillis() + 60_000; throw e }
            records.filter { it.name == name }
        }
    }

    suspend fun liveWiki(): List<DirectorySource> = withContext(Dispatchers.IO) {
        parseLiveWiki(Jsoup.parse(fetchPublic("https://fmhy.net/video", "text/html"), "https://fmhy.net/video"))
    }

    internal fun parseLiveWiki(document: Document): List<DirectorySource> {
        var section = document.select("h3").firstOrNull { it.id() == "live-sports" || it.text().startsWith("Live Sports") }?.nextElementSibling()
        val records = mutableListOf<DirectorySource>()
        while (section != null && section.tagName() !in setOf("h2", "h3")) {
            section.select("li").forEach { row ->
                if (row.select("a").firstOrNull()?.text()?.trim() != "Streamed") return@forEach
                row.select("a[href]").filter { it.text().trim() == "Streamed" || it.text().trim().matches(Regex("[2-9]")) }.forEach { link ->
                    safeDirectoryAddress(link.absUrl("href"))?.let {
                        records += DirectorySource("Streamed", "streamed", it, "candidate", "Wiki FMHY", "https://fmhy.net/video#live-sports")
                    }
                }
            }
            section = section.nextElementSibling()
        }
        return records.distinctBy { it.url }.take(4)
    }

    internal fun parseWiki(document: Document): List<DirectorySource> {
        var section = document.select("h2").firstOrNull { it.text().contains("French / Français", true) }?.nextElementSibling()
        var streaming = false
        val records = mutableListOf<DirectorySource>()
        while (section != null && section.tagName() != "h2") {
            if (section.tagName() == "h3") streaming = section.text().contains("Streaming", true)
            if (streaming) section.select("li a[href]").forEach { link ->
                val label = link.text().lowercase().filter(Char::isLetterOrDigit)
                val name = wikiNames[label] ?: return@forEach
                val address = safeDirectoryAddress(link.absUrl("href")) ?: return@forEach
                records += DirectorySource(name, label, address, "candidate", "Wiki FMHY", "https://fmhy.net/non-english")
            }
            section = section.nextElementSibling()
        }
        return records.distinctBy { "${it.name}|${it.url}" }
    }
}

internal fun safeDirectoryAddress(value: String): String? = value.toHttpUrlOrNull()?.takeIf {
    it.isHttps && it.username.isEmpty() && it.password.isEmpty() && it.host.contains('.') &&
        it.host.any(Char::isLetter) && ':' !in it.host && !it.host.endsWith(".local") &&
        it.host !in setOf("localhost", "t.me", "telegram.me") && it.query == null && it.fragment == null
}?.toString()

internal fun reportedDirectoryStatus(status: String): String = when (status) {
    "active" -> "En ligne selon l’annuaire"
    "redirected" -> "Changement d’adresse signalé"
    "blocked" -> "Accès bloqué signalé · pas une panne confirmée"
    "down", "offline" -> "Hors ligne selon l’annuaire"
    "maintenance" -> "Maintenance signalée"
    "candidate" -> "Adresse proposée par le wiki · état à vérifier"
    else -> "État inconnu de l’annuaire"
}

/** One site can own several directory records (mirrors, regional copies). Choose the healthiest record instead of discarding them all. */
internal fun selectDirectoryEntry(entries: List<DirectorySource>, aliases: Map<String, String>, name: String): DirectorySource? {
    fun key(value: String) = value.lowercase().filter { it.isLetterOrDigit() }
    fun rank(entry: DirectorySource) = when (entry.status) {
        "active" -> 3
        "redirected" -> 2
        "candidate" -> 1
        else -> 0
    }
    fun best(matches: List<DirectorySource>) = matches.maxWithOrNull(compareBy { rank(it) })
    return best(entries.filter { key(it.slug) == key(aliases[name] ?: name) })
        ?: best(entries.filter { key(it.name) == key(name) })
}
