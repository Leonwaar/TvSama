package fr.nekotv

import java.text.Normalizer
import java.util.Locale

object CatalogIdentity {
    /** Providers publish IMDb ids bare ("tt0111161") or inside a URL; keep only the id. */
    internal fun imdbId(value: String?): String? =
        Regex("tt\\d{6,}").find(value.orEmpty())?.value

    /** External ids are stronger than a title: they group the same film across providers. */
    private fun idKeys(item: Anime): List<String> = listOfNotNull(
        item.tmdbId?.takeIf { it > 0 }?.let { "${item.tag}|tmdb:$it" },
        imdbId(item.imdbId)?.let { "${item.tag}|imdb:$it" }
    )

    /** A provider may list a film as a series. IMDb/TMDB ids are safe across that mislabelling. */
    private fun globalIdKeys(item: Anime): List<String> = listOfNotNull(
        item.tmdbId?.takeIf { it > 0 }?.let { "tmdb:$it" },
        imdbId(item.imdbId)?.let { "imdb:$it" }
    )

    private val movieMarker = Regex("(?i)(?<![a-z])(?:(?:the|le|la|les)\\s+)?(?:movie|film)(\\s*(?:n[°o]?\\s*)?(\\d+))?(?![a-z])")
    private val yearToken = Regex("(?<!\\d)(19[0-9]{2}|20[0-9]{2})(?!\\d)")

    /** Some catalogues publish a film as a series ("Hunter x Hunter Movie 1"). */
    fun isMovieTitle(value: String): Boolean = movieMarker.containsMatchIn(value)

    /** Canonicalises "Movie 1" / "The Movie" without erasing a distinguishing number:
     *  numbered films must never collapse into each other, but "Movie 2: The Last Mission"
     *  still has to meet the same film published under its real title. */
    fun filmTitle(value: String): String {
        val marker = movieMarker.find(value)
        if (marker == null) return title(value)
        val number = marker.groupValues.getOrNull(2).orEmpty()
        val before = value.substring(0, marker.range.first)
        val after = value.substring(marker.range.last + 1)
        // The number only carries the identity when no real title follows the marker.
        val remainder = title(after)
        val rebuilt = if (remainder.isBlank()) "$before movie $number" else "$before $remainder"
        val stripped = title(rebuilt)
        return stripped.ifBlank { title(value) }
    }

    /** A year written in the title is only trusted when the provider has no release year. */
    fun titleYear(value: String): Int? = yearToken.find(value)?.value?.toIntOrNull()?.takeIf { it in 1900..2100 }
    fun effectiveYear(item: Anime): Int? = item.year
        ?: (listOf(item.title) + item.aliases).firstNotNullOfOrNull { titleYear(it) }

    /** Update cards in place, then append discoveries; never move a focused poster. */
    fun appendStable(existing: List<Anime>, incoming: List<Anime>): List<Anime> {
        val result = existing.toMutableList()
        val byTitle = mutableMapOf<String, MutableSet<Int>>()
        fun keys(item: Anime): List<String> {
            val titles = (listOf(item.title) + item.aliases).map(::identityTitle).filter(String::isNotBlank).distinct()
            val filmish = item.tag == "Film" || (listOf(item.title) + item.aliases).any(::isMovieTitle)
            val filmTitles = (listOf(item.title) + item.aliases).filter(::isMovieTitle).map { identityTitle(filmTitle(it)) }
                .filter(String::isNotBlank).distinct()
            // A Film and a Série card that describe the same film must meet each other;
            // the provider tag alone must not keep duplicates alive across commits.
            return titles.map { "${item.tag}|$it" } + idKeys(item) + globalIdKeys(item) +
                (if (filmish) titles.map { "*|$it" } + filmTitles.map { "*|film:$it" } else emptyList())
        }
        fun register(item: Anime, index: Int) { keys(item).forEach { byTitle.getOrPut(it) { linkedSetOf() }.add(index) } }
        result.forEachIndexed { index, item -> register(item, index) }
        for (item in merge(existing + incoming)) {
            val candidates = keys(item).flatMap { byTitle[it].orEmpty() }.distinct()
            val ambiguous = item.year == null && candidates.mapNotNull { result[it].year }.distinct().size > 1
            val index = if (ambiguous) null else candidates.firstOrNull { index ->
                val old = result[index]
                val itemYear = effectiveYear(item)
                val oldYear = effectiveYear(old)
                oldYear == null || itemYear == null || oldYear == itemYear
            }
            if (index == null) { result += item; register(item, result.lastIndex) } else {
                val old = result[index]
                result[index] = old.copy(
                    year = old.year ?: item.year,
                    tmdbId = old.tmdbId ?: item.tmdbId, imdbId = old.imdbId ?: item.imdbId,
                    popularity = maxOf(old.popularity, item.popularity), rating = if (item.voteCount > old.voteCount) item.rating else old.rating, voteCount = maxOf(old.voteCount, item.voteCount),
                    description = old.description.ifBlank { item.description }, genres = (old.genres + item.genres).distinct(), firstAired = old.firstAired ?: item.firstAired,
                    poster = old.poster.ifBlank { item.poster },
                    aliases = (old.aliases + item.aliases + item.title).distinct(),
                    localizedTitles = old.localizedTitles + item.localizedTitles,
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
        TitleAliases.seasonNumber(value)?.let { return it }
        return numberedSuffix.find(clean)?.groupValues?.get(1)?.toIntOrNull()
            ?.takeIf { title(numberedBase(clean)) == title(seriesTitle(series)) }
    }
    fun title(value: String): String {
        val normalized = Normalizer.normalize(TitleAliases.canonical(seriesTitle(value).replace(languageWords, " ").trim()).lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(combiningMarks, "")
        .replace(languageWords, " ")
        .replace(nonLetters, "")

        return when (normalized) {
            "classroomofelite", "classroomoftheelite", "youkosojitsuryokushijoushuginokyoushitsue", "youkosojitsuryokushijoushuginokyoushitsueyoujitsu" -> "classroomoftheelite"
            else -> normalized
        }
    }

    // Providers often disambiguate editions with "(2011)" or " - 2011". That year belongs to
    // the edition, not to the title: ignoring it lets the same edition from two catalogues meet,
    // while effectiveYear() still keeps 1999 and 2011 apart.
    private val yearInBrackets = Regex("(?i)\\s*[\\(\\[]\\s*(?:19|20)\\d{2}\\s*[\\)\\]]\\s*$")
    private val yearAfterDash = Regex("(?i)\\s+[-–—]\\s+(?:19|20)\\d{2}\\s*$")
    // Some catalogues omit the parentheses: "Hunter x Hunter 2011".
    // Keep the year as edition data through effectiveYear(), but do not let it create
    // a second title identity. A title consisting only of a year is intentionally untouched.
    private val bareTrailingYear = Regex("(?i)\\s+(?:19|20)\\d{2}\\s*$")
    private val knownVariantSuffix = Regex("(?i)\\s+(?:kai)\\s*$")
    fun identityTitle(value: String): String {
        val bracketed = value.replace(yearInBrackets, " ").replace(yearAfterDash, " ").trim()
        val candidate = bracketed.replace(bareTrailingYear, " ").replace(knownVariantSuffix, " ").trim()
        // Bare numeric/Kai suffixes are only aliases when the base is a known title family.
        // Never strip arbitrary catalogue titles such as "Titre 1".
        val base = title(candidate)
        val isKnownFamily = TitleAliases.variants(candidate).isNotEmpty() ||
            TitleAliases.variants(value).isNotEmpty()
        return title(if (isKnownFamily && candidate.isNotBlank()) candidate else bracketed)
    }

    fun merge(items: List<Anime>): List<Anime> {
        val bases = items.filter { it.tag == "Série" }.map { title(seriesTitle(it.title)) }.toSet()
        val cleaned = items.map { item ->
            if (item.tag == "Série") {
                val explicit = seriesTitle(item.title)
                val base = numberedBase(explicit)
                val clean = if (title(base) in bases) base else explicit
                item.copy(title = clean, aliases = (listOf(item.title) + item.aliases).distinct(), year = if (seasonSuffix.containsMatchIn(item.title.replace(languageSuffix, "")) || clean != explicit || TitleAliases.seasonNumber(item.title) != null) null else item.year)
            } else item
        }
        val parents = mutableMapOf<String, String>()
        fun root(key: String): String {
            val parent = parents[key] ?: return key.also { parents[key] = it }
            return if (parent == key) key else root(parent).also { parents[key] = it }
        }
        cleaned.forEach { item ->
            val keys = (listOf(item.title) + item.aliases).map(::identityTitle).filter { it.isNotBlank() }.map { "${item.tag}|$it" } +
                idKeys(item)
            val first = keys.firstOrNull()?.let(::root) ?: return@forEach
            keys.drop(1).forEach { parents[root(it)] = root(first) }
        }
        return cleaned.groupBy { root("${it.tag}|${identityTitle(it.title)}") }.values.flatMap { sameTitle ->
            // A shared TMDB/IMDb id proves it is the same film, even when providers disagree
            // on the year. Only ambiguous remakes fall back to year grouping.
            val identified = sameTitle.mapNotNull { item -> idKeys(item).firstOrNull()?.let { item to it } }
            val commonId = identified.map { it.second }.distinct().singleOrNull()
            val groups = if (commonId != null && identified.size == sameTitle.size) listOf(sameTitle) else {
                val years = sameTitle.mapNotNull(::effectiveYear).distinct()
                // An unknown year joins a single known edition, but never arbitrarily picks a remake.
                sameTitle.groupBy { effectiveYear(it) ?: years.singleOrNull() }.values.toList()
            }
            groups
        }.let(::coalesceFilms).map(::combine)
    }

    /** Providers disagree on whether a film is a "Film" or a "Série" card. Merge the
     *  copies that share a film marker, a TMDB/IMDb id, or an identical film title. */
    private fun coalesceFilms(groups: List<List<Anime>>): List<List<Anime>> {
        if (groups.size < 2) return groups
        data class Info(val tags: Set<String>, val years: Set<Int>, val titles: Set<String>,
            val ids: Set<String>, val marker: Boolean)
        val info = groups.map { group ->
            Info(
                tags = group.map { it.tag }.toSet(),
                years = group.mapNotNull(::effectiveYear).toSet(),
                titles = group.flatMap { listOf(it.title) + it.aliases }.map { identityTitle(filmTitle(it)) }.filter { it.isNotBlank() }.toSet(),
                ids = group.flatMap(::globalIdKeys).toSet(),
                marker = group.any { item -> (listOf(item.title) + item.aliases).any(::isMovieTitle) })
        }
        val parents = IntArray(groups.size) { it }
        fun root(index: Int): Int = if (parents[index] == index) index else root(parents[index]).also { parents[index] = it }
        fun union(a: Int, b: Int) { parents[root(a)] = root(b) }
        fun compatible(a: Info, b: Info) = a.years.isEmpty() || b.years.isEmpty() || a.years.any { it in b.years }
        for (i in groups.indices) for (j in i + 1 until groups.size) {
            val a = info[i]
            val b = info[j]
            if ("Direct" in a.tags || "Direct" in b.tags || "Flux personnel" in a.tags || "Flux personnel" in b.tags) continue
            val sharedId = a.ids.any { it in b.ids }
            // An IMDb/TMDB id is proof of identity, even when providers disagree on the year.
            if (!sharedId && !compatible(a, b)) continue
            val sharedTitle = a.titles.any { it in b.titles }
            // A shared title only joins two cross-tag cards when a provider explicitly marks
            // one as a film, or both publish the same year.
            val sameEdition = a.years.isNotEmpty() && a.years == b.years
            if (sharedId || sharedTitle && (a.marker || b.marker || a.tags != b.tags && sameEdition)) union(i, j)
        }
        return groups.indices.groupBy(::root).values.map { indices -> indices.flatMap { groups[it] } }
    }

    private fun combine(group: List<Anime>): Anime {
        val first = group.firstOrNull { it.tag == "Film" }
            ?: group.firstOrNull { item -> item.tag != "Série" ||
                (!seasonSuffix.containsMatchIn(item.aliases.firstOrNull().orEmpty().replace(languageSuffix, "")) &&
                    TitleAliases.seasonNumber(item.aliases.firstOrNull().orEmpty()) == null) }
            ?: group.first()
        return first.copy(year = group.firstNotNullOfOrNull { it.year },
            tmdbId = group.firstNotNullOfOrNull { it.tmdbId },
            imdbId = group.firstNotNullOfOrNull { imdbId(it.imdbId) },
            popularity = group.maxOf { it.popularity }, rating = group.maxByOrNull { it.voteCount }?.rating ?: first.rating, voteCount = group.maxOf { it.voteCount },
            description = group.firstNotNullOfOrNull { it.description.takeIf(String::isNotBlank) }.orEmpty(),
            genres = group.flatMap { it.genres }.distinct(), firstAired = group.firstNotNullOfOrNull { it.firstAired },
            poster = first.poster.ifBlank { group.firstNotNullOfOrNull { it.poster.takeIf(String::isNotBlank) }.orEmpty() },
            aliases = group.flatMap { listOf(it.title) + it.aliases }.distinct(),
            localizedTitles = group.fold(emptyMap<String, String>()) { acc, item -> acc + item.localizedTitles },
            references = group.flatMap { it.references + MediaReference(it.provider, it.id, it.tag) }.distinct())
    }
}
