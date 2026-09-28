package fr.nekotv

import java.text.Normalizer
import java.util.Locale

object CatalogIdentity {
    private val seasonSuffix = Regex("(?i)\\s*[-–:(]?\\s*(?:(?:saison|season)\\s*\\d+|\\d+(?:st|nd|rd|th)\\s+season)\\s*[)]?\\s*$")
    private val languageSuffix = Regex("(?i)\\s*[(\\[]?(?:VF|VOSTFR|VFF|VFQ|FR)[)\\]]?\\s*$")
    private val numberedSuffix = Regex("\\s+([1-9][0-9]?)$")
    fun seriesTitle(value: String): String = value.replace(languageSuffix, "").replace(seasonSuffix, "").trim()
    fun numberedBase(value: String): String = seriesTitle(value).replace(numberedSuffix, "").trim()
    fun seasonNumber(value: String, series: String): Int? {
        val clean = value.replace(languageSuffix, "").trim()
        val explicit = Regex("(?i)(?:saison|season)\\s*([0-9]+)|([0-9]+)(?:st|nd|rd|th)\\s+season").find(clean)
        if (explicit != null) return explicit.groupValues.drop(1).firstNotNullOfOrNull { it.toIntOrNull() }
        return numberedSuffix.find(clean)?.groupValues?.get(1)?.toIntOrNull()
            ?.takeIf { title(numberedBase(clean)) == title(seriesTitle(series)) }
    }
    fun title(value: String): String {
        val normalized = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "")
        .replace(Regex("(?i)(?<![a-z])(truefrench|french|vfq|vff|vostfr|vost|vf|vo)(?![a-z])"), " ")
        .replace(Regex("[^a-z0-9]"), "")

        return when (normalized) {
            "classroomofelite", "classroomoftheelite", "youkosojitsuryokushijoushuginokyoushitsue", "youkosojitsuryokushijoushuginokyoushitsueyoujitsu" -> "classroomoftheelite"
            else -> normalized
        }
    }

    fun merge(items: List<Anime>): List<Anime> {
        val bases = items.filter { it.tag == "Série" }.map { title(seriesTitle(it.title)) }.toSet()
        return items.map { item ->
            if (item.tag == "Série") {
                val explicit = seriesTitle(item.title)
                val base = numberedBase(explicit)
                val clean = if (title(base) in bases) base else explicit
                item.copy(title = clean, year = if (seasonSuffix.containsMatchIn(item.title.replace(languageSuffix, "")) || clean != explicit) null else item.year)
            } else item
        }.groupBy { "${title(it.title)}|${it.tag}" }.values.flatMap { sameTitle ->
            val years = sameTitle.mapNotNull { it.year }.distinct()
            // An unknown year joins a single known edition, but never arbitrarily picks a remake.
            sameTitle.groupBy { it.year ?: years.singleOrNull() }.values.map { group ->
                val first = group.first()
                first.copy(year = group.firstNotNullOfOrNull { it.year },
                    poster = group.firstNotNullOfOrNull { it.poster.takeIf(String::isNotBlank) }.orEmpty(),
                    references = group.flatMap { it.references + MediaReference(it.provider, it.id, it.tag) }.distinct())
            }
        }
    }
}
