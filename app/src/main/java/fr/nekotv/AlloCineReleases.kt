package fr.nekotv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/** AlloCiné supplies cinema metadata only; playback references come from enabled catalogues. */
internal object AlloCineReleases {
    private val client by lazy { com.streamflixreborn.streamflix.utils.NetworkClient.extractor.newBuilder()
        .callTimeout(5, TimeUnit.SECONDS).build() }
    suspend fun load(): List<Anime> = withContext(Dispatchers.IO) {
        val url = "https://www.allocine.fr/film/aucinema/"
        client.newCall(Request.Builder().url(url).header("User-Agent", com.streamflixreborn.streamflix.providers.TvSamaAnimeProvider.USER_AGENT).build()).execute().use { response ->
            check(response.isSuccessful) { "AlloCiné : HTTP ${response.code}" }
            parse(Jsoup.parse(response.body?.string().orEmpty(), url)).also { check(it.isNotEmpty()) { "Sorties cinéma non disponibles" } }
        }
    }
    internal fun parse(doc: Document, today: LocalDate = LocalDate.now()): List<Anime> = doc.select(".entity-card").mapNotNull { card ->
        val link = card.selectFirst("a.meta-title-link[href]") ?: return@mapNotNull null
        val title = link.text().trim().takeIf(String::isNotBlank) ?: return@mapNotNull null
        val date = runCatching { LocalDate.parse(card.selectFirst(".meta-body-info .date")?.text(), DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.FRENCH)) }.getOrNull()
            ?: return@mapNotNull null
        if (date.isAfter(today) || date.isBefore(today.minusMonths(6))) return@mapNotNull null
        val image = card.selectFirst("img.thumbnail-img")
        val poster = image?.absUrl("data-src").orEmpty().ifBlank { image?.absUrl("src").orEmpty() }
        val rating = card.select(".rating-item").firstOrNull { it.selectFirst(".rating-title")?.text()?.contains("Spectateurs") == true }
            ?.selectFirst(".stareval-note")?.text()?.replace(',', '.')?.toDoubleOrNull()?.times(2) ?: 0.0
        Anime(title, "Film", emptyList(), link.absUrl("href"), poster, card.selectFirst(".synopsis .content-txt")?.text().orEmpty(), date.year,
            card.select(".meta-body-info .dark-grey-link").map { it.text() }, firstAired = date.toString(), rating = rating)
    }.distinctBy { it.id }
}
