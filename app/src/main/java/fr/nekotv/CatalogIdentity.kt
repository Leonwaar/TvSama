package fr.nekotv

import java.text.Normalizer
import java.util.Locale

object CatalogIdentity {
    /** Update cards in place, then append discoveries; never move a focused poster. */
    fun appendStable(existing: List<Anime>, incoming: List<Anime>): List<Anime> {
        val result = existing.toMutableList()
        val byTitle = mutableMapOf<String, MutableSet<Int>>()
        fun keys(item: Anime) = (listOf(item.title) + item.aliases).map(::title).filter(String::isNotBlank).distinct().map { "${item.tag}|$it" }
        fun register(item: Anime, index: Int) { keys(item).forEach { byTitle.getOrPut(it) { linkedSetOf() }.add(index) } }
        result.forEachIndexed { index, item -> register(item, index) }
        for (item in incoming) {
            val candidates = keys(item).flatMap { byTitle[it].orEmpty() }.distinct()
            val ambiguous = item.year == null && candidates.mapNotNull { result[it].year }.distinct().size > 1
            val index = if (ambiguous) null else candidates.firstOrNull { index ->
                val old = result[index]
                old.year == null || item.year == null || old.year == item.year
            }
            if (index == null) { result += item; register(item, result.lastIndex) } else {
                val old = result[index]
                result[index] = old.copy(
                    year = old.year ?: item.year,
                    poster = old.poster.ifBlank { item.poster },
                    aliases = (old.aliases + item.aliases + item.title).distinct(),
                    references = (old.references + MediaReference(old.provider, old.id, old.tag) + item.references + MediaReference(item.provider, item.id, item.tag)).distinct())
                register(result[index], index)
            }
        }
        return result
    }
    private val seasonSuffix = Regex("(?i)\\s*[-–:(]?\\s*(?:(?:saison|season)\\s*\\d+|\\d+(?:st|nd|rd|th)\\s+season)\\s*[)]?\\s*$")
    private val languageSuffix = Regex("(?i)\\s*[(\\[]?(?:VF|VOSTFR|VFF|VFQ|FR)[)\\]]?\\s*$")
    private val numberedSuffix = Regex("\\s+([1-9][0-9]?)$")
    private val combiningMarks = Regex("\\p{M}")
    private val languageWords = Regex("(?i)(?<![a-z])(truefrench|french|vfq|vff|vostfr|vost|vf|vo)(?![a-z])")
    private val nonLetters = Regex("[^\\p{L}\\p{N}]")
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
        val normalized = Normalizer.normalize(TitleAliases.canonical(value).lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(combiningMarks, "")
        .replace(languageWords, " ")
        .replace(nonLetters, "")

        return when (normalized) {
            "classroomofelite", "classroomoftheelite", "youkosojitsuryokushijoushuginokyoushitsue", "youkosojitsuryokushijoushuginokyoushitsueyoujitsu" -> "classroomoftheelite"
            else -> normalized
        }
    }

    fun merge(items: List<Anime>): List<Anime> {
        val bases = items.filter { it.tag == "Série" }.map { title(seriesTitle(it.title)) }.toSet()
        val cleaned = items.map { item ->
            if (item.tag == "Série") {
                val explicit = seriesTitle(item.title)
                val base = numberedBase(explicit)
                val clean = if (title(base) in bases) base else explicit
                item.copy(title = clean, year = if (seasonSuffix.containsMatchIn(item.title.replace(languageSuffix, "")) || clean != explicit) null else item.year)
            } else item
        }
        val parents = mutableMapOf<String, String>()
        fun root(key: String): String {
            val parent = parents[key] ?: return key.also { parents[key] = it }
            return if (parent == key) key else root(parent).also { parents[key] = it }
        }
        cleaned.forEach { item ->
            val keys = (listOf(item.title) + item.aliases).map(::title).filter { it.isNotBlank() }.map { "${item.tag}|$it" } +
                listOfNotNull(item.imdbId?.takeIf { it.startsWith("tt") }?.let { "${item.tag}|imdb:$it" })
            val first = keys.firstOrNull()?.let(::root) ?: return@forEach
            keys.drop(1).forEach { parents[root(it)] = root(first) }
        }
        return cleaned.groupBy { root("${it.tag}|${title(it.title)}") }.values.flatMap { sameTitle ->
            val years = sameTitle.mapNotNull { it.year }.distinct()
            // An unknown year joins a single known edition, but never arbitrarily picks a remake.
            sameTitle.groupBy { it.year ?: years.singleOrNull() }.values.map { group ->
                val first = group.first()
                first.copy(year = group.firstNotNullOfOrNull { it.year },
                    poster = group.firstNotNullOfOrNull { it.poster.takeIf(String::isNotBlank) }.orEmpty(),
                    aliases = group.flatMap { listOf(it.title) + it.aliases }.distinct(),
                    references = group.flatMap { it.references + MediaReference(it.provider, it.id, it.tag) }.distinct())
            }
        }
    }
}
