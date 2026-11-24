package fr.nekotv

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt
import java.text.Normalizer
import java.util.Locale
import java.time.Instant
import java.time.ZoneOffset

/** Local, recency-weighted content ranking with a deliberately mixed discovery feed. */
internal object Recommendations {
    private val themes = mapOf(
        "adventure" to "aventure adventure voyage quete quest aventureux",
        "action" to "action combat bataille battle guerre war superheros superhero ninja samourai",
        "fantasy" to "fantastique fantasy magie magic demon dragon surnaturel supernatural pouvoir powers",
        "scifi" to "science fiction scifi espace space futur robot alien apocalypse",
        "comedy" to "comedie comedy comique humour humor drole funny sitcom",
        "drama" to "drame drama famille family emotion adolescence school ecole",
        "romance" to "romance romantique romantic amour love couple",
        "thriller" to "thriller suspense crime enquete detective meurtre murder mystere mystery",
        "horror" to "horreur horror zombie peur fear epouvante",
        "animation" to "anime animes manga animation animee shonen shounen seinen")
    private val stop = "avec dans pour une des les est qui que sur par the and this from that you his her saison season episode film serie vostfr".split(' ').toSet()
    private val combiningMarks = Regex("\\p{M}")
    private val wordBoundaries = Regex("[^\\p{L}\\p{N}]+")
    private val themeWords by lazy { themes.mapValues { words(it.value) } }

    fun displayed(catalogue: List<Anime>, ranked: List<Anime>, previous: List<Anime> = emptyList(), loading: Boolean = false): List<Anime> {
        fun key(item: Anime) = "${item.provider}|${item.tag}|${item.id}"
        val available = catalogue.distinctBy(::key)
        val current = available.associateBy(::key)
        val ordered = ranked.mapNotNull { current[key(it)] }.distinctBy(::key)
        val keys = ordered.map(::key).toSet()
        val incoming = ordered + available.filter { key(it) !in keys }
        if (previous.isEmpty()) return incoming
        // Keep previous items when loading OR when incoming is empty to prevent feed jumping
        if (loading || incoming.isEmpty()) return CatalogIdentity.appendStable(previous, incoming)
        val references = available.flatMap { it.references + MediaReference(it.provider, it.id, it.tag) }.toSet()
        val retained = previous.filter { item ->
            (item.references + MediaReference(item.provider, item.id, item.tag)).any { it in references }
        }
        return CatalogIdentity.appendStable(retained, incoming)
    }


    /** Damerau-Levenshtein distance normalized to a 0..1 similarity score. */
    private fun damerauLevenshtein(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1])
                d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + cost)
        }
        return 1.0 - d[a.length][b.length].toDouble() / maxOf(a.length, b.length)
    }

    /**
     * Comprehensive search result ranking based on:
     * 1. Text match (titles + aliases)
     * 2. Popularity (log scale)
     * 3. Bayesian rating
     * 4. Freshness (recency)
     * 5. Completeness of metadata
     */
    fun rankSearchResults(
        items: List<Anime>,
        query: String,
        now: Long = System.currentTimeMillis()
    ): List<Anime> {
        val trimmedQuery = query.trim()
        val sanitizedQuery = SearchSanitizer.sanitize(trimmedQuery) ?: return items.sortedByDescending { it.popularity }
        val queryTokens = Normalize.tokens(sanitizedQuery)
        if (queryTokens.isEmpty()) return items.sortedByDescending { it.popularity }

        val currentYear = Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).year
        
        // Extract query indices
        val queryYear = Regex("(?:19|20)\\d{2}").find(sanitizedQuery)?.value?.toIntOrNull()
        val queryIsMovie = sanitizedQuery.contains("film", true) || sanitizedQuery.contains("movie", true)
        val queryIsSeries = sanitizedQuery.contains("série", true) || sanitizedQuery.contains("serie", true) || sanitizedQuery.contains("season", true) || sanitizedQuery.contains("saison", true)

        fun textScore(item: Anime): Double {
            val allTitles = (listOf(item.title) + item.aliases).map { Normalize.normalizeTitle(it) }
            val q = Normalize.normalizeTitle(sanitizedQuery)
            
            return allTitles.maxOf { title ->
                when {
                    title == q -> 1.0 // Exact match
                    title.startsWith(q) -> 0.9
                    title.contains(q) -> 0.7
                    else -> {
                        val titleTokens = Normalize.tokens(title)
                        val shared = titleTokens.intersect(queryTokens).size
                        if (shared > 0) (shared.toDouble() / queryTokens.size.coerceAtLeast(1)) * 0.5 else 0.0
                        // Damerau-Levenshtein fuzzy match on the full normalized title
                        .coerceAtLeast(damerauLevenshtein(q, title))
                    }
                }
            }
        }

        fun popularityScore(item: Anime): Double {
            val pop = item.popularity.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
            return ln(1.0 + pop) / ln(1.0 + 10000.0)
        }

        fun qualityScore(item: Anime): Double {
            val rating = item.rating.takeIf { it.isFinite() }?.coerceIn(0.0, 10.0) ?: 0.0
            val votes = item.voteCount.coerceAtLeast(0)
            return ((rating * votes + 6.0 * 50) / (votes + 50.0)) / 10.0
        }

        fun freshnessScore(item: Anime): Double {
            val year = item.firstAired?.take(4)?.toIntOrNull() ?: item.year ?: return 0.2
            if (year > currentYear) return 0.0
            return exp(-(currentYear - year) / 10.0)
        }

        fun completenessScore(item: Anime): Double {
            var score = 0.0
            if (item.poster.isNotBlank()) score += 0.3
            if (SynopsisResolver.resolveOverview(item).text.isNotBlank()) score += 0.3
            if (item.genres.isNotEmpty()) score += 0.2
            return score
        }

        return items.map { item ->
            var score = (textScore(item) * 10.0) +
                        (popularityScore(item) * 2.0) +
                        (qualityScore(item) * 1.5) +
                        (freshnessScore(item) * 1.0) +
                        (completenessScore(item) * 0.5)

            // Query index boosts
            if (queryYear != null && CatalogIdentity.effectiveYear(item) == queryYear) score += 5.0
            if (queryIsMovie && item.tag == "Film") score += 2.0
            if (queryIsSeries && item.tag != "Film") score += 2.0

            // Bonus si lisible sur mon service (has native playable source metadata)
            if (item.episodes.any { it.sources.isNotEmpty() }) score += 1.5

            // Boost léger si déjà vu/commencé (optionnel)
            if (item.poster.isNotBlank() && item.id.isNotEmpty()) score += 0.8

            // a match exact always wins over popularity: ensure a huge gap
            if (textScore(item) >= 0.9) score += 100.0

            item to score
        }.sortedByDescending { it.second }.map { it.first }.take(SearchService.MAX_RESULTS)
    }
    private fun words(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD).replace(combiningMarks, "")
        .lowercase(Locale.ROOT).split(wordBoundaries)
        .filter { it.length > 2 && it !in stop }.toSet()
    private fun features(item: Anime): Set<String> {
        val tokens = words((listOf(item.title, SynopsisResolver.resolveOverview(item).text) + item.aliases + item.genres).joinToString(" "))
        val concepts = themeWords.filterValues { vocabulary -> vocabulary.any { it in tokens } }.keys
        return tokens + concepts.map { "theme:$it" } + item.genres.flatMap { words(it) }.map { "genre:$it" }
    }
    fun rank(catalogue: List<Anime>, history: List<SavedPlayback>, favorites: List<Anime>, searches: List<String>, searchMatches: List<Anime> = emptyList(), now: Long = System.currentTimeMillis()): List<Anime> {
        if (catalogue.isEmpty()) return emptyList()
        
        // Optimization: Avoid creating many intermediate lists/sets. 
        // Use sequences for large catalog processing to reduce memory pressure.
        val all = (catalogue + history.map { it.anime } + favorites + searchMatches).distinct()
        val vectors = all.associateWith(::features)
        val frequency = vectors.values.flatten().groupingBy { it }.eachCount()
        val profile = mutableMapOf<String, Double>()
        
        fun learn(item: Anime, weight: Double) {
            val tokens = vectors[item] ?: features(item)
            val norm = sqrt(tokens.size.coerceAtLeast(1).toDouble())
            tokens.forEach { token -> profile[token] = (profile[token] ?: 0.0) + weight / norm }
        }
        
        history.groupBy { CatalogIdentity.title(it.anime.title) }.values.forEach { entries ->
            val last = entries.maxByOrNull { it.updatedAt } ?: return@forEach
            val age = (now - last.updatedAt).coerceAtLeast(0).toDouble() / (86_400_000 * 30.0)
            val engagement = if (last.duration > 0) (last.position.toDouble() / last.duration).coerceIn(0.0, 1.0) else 0.2
            learn(last.anime, exp(-age) * (1.0 + engagement + ln(1.0 + entries.size)))
        }
        favorites.forEach { learn(it, 2.5) }
        
        searches.take(20).forEachIndexed { index, search ->
            val weight = 1.5 * exp(-index / 6.0)
            val query = words(search)
            query.forEach { token -> profile[token] = (profile[token] ?: 0.0) + weight }
            themeWords.filterValues { it.any { token -> token in query } }.keys.forEach { theme ->
                profile["theme:$theme"] = (profile["theme:$theme"] ?: 0.0) + weight
            }
            
            // Optimization: Use sequence for filtering and take(3) to avoid creating a large list
            val matches = all.asSequence()
                .distinctBy { CatalogIdentity.title(it.title) }
                .filter { item ->
                    (listOf(item.title) + item.aliases).any { title ->
                        val titleWords = words(title)
                        query.isNotEmpty() && titleWords.containsAll(query)
                    }
                }
                .take(3)
                .toList()
            
            matches.forEach { learn(it, weight / matches.size.coerceAtLeast(1)) }
        }
        
        val seen = history.map { CatalogIdentity.title(it.anime.title) }.toSet()
        val maxPopularity = catalogue.maxOfOrNull { it.popularity.takeIf(Double::isFinite)?.coerceAtLeast(0.0) ?: 0.0 }?.coerceAtLeast(1.0) ?: 1.0
        val currentYear = Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).year
        
        fun popularity(item: Anime) = if (item.popularity.isFinite() && item.popularity > 0)
            ln(1.0 + item.popularity) / ln(1.0 + maxPopularity) else 0.12
        fun freshness(item: Anime): Double {
            val year = item.firstAired?.take(4)?.toIntOrNull() ?: item.year ?: return 0.15
            if (year > currentYear) return 0.0
            return exp(-(currentYear - year) / 6.0)
        }
        fun quality(item: Anime): Double {
            val rating = item.rating.takeIf(Double::isFinite)?.coerceIn(0.0, 10.0) ?: 0.0
            return ((rating * item.voteCount.coerceAtLeast(0) + 6.0 * 100) / (item.voteCount.coerceAtLeast(0) + 100.0)) / 10.0
        }
        
        val profileNorm = sqrt(profile.values.sumOf { it * it }).coerceAtLeast(1.0)
        
        // Memory Optimization: Calculate affinity and discovery on the fly or in a single pass 
        // instead of creating multiple large maps for the entire catalogue.
        val ranked = catalogue.associateWith { item ->
            val tokens = vectors[item] ?: features(item)
            val weights = tokens.associateWith { token ->
                ln(1.0 + vectors.size.toDouble() / (frequency[token] ?: 1)) * if (token.startsWith("theme:")) 2.0 else 1.0
            }
            val dot = weights.entries.sumOf { (token, weight) -> (profile[token] ?: 0.0) * weight }
            val norm = sqrt(weights.values.sumOf { it * it }).coerceAtLeast(1.0)
            val affinityValue = (dot / (norm * profileNorm)).coerceIn(0.0, 1.0)
            
            val discValue = popularity(item) * 1.3 + freshness(item) * 0.6 + quality(item) * 0.5
            
            affinityValue * 2.4 + discValue -
                (if (CatalogIdentity.title(item.title) in seen) 1.5 else 0.0) -
                (if (item.poster.isBlank()) 0.08 else 0.0)
        }
        
        val seenItems = catalogue.filter { CatalogIdentity.title(it.title) in seen }.toSet()
        val remaining = catalogue.distinct().sortedWith(compareBy<Anime> { it in seenItems }.thenByDescending { ranked[it] ?: 0.0 }).toMutableList()
        val result = mutableListOf<Anime>()
        
        while (remaining.isNotEmpty() && result.size < 120) {
            val recent = result.takeLast(5)
            val explore = result.size % 5 == 4
            val unseen = remaining.filter { it !in seenItems }.ifEmpty { remaining }
            
            val next = (if (explore) {
                unseen.sortedByDescending { item -> 
                    val pop = popularity(item) * 1.3 + freshness(item) * 0.6 + quality(item) * 0.5
                    pop - if (item in seenItems) 1.5 else 0.0 
                }.take(48)
            } else {
                unseen.take(48)
            }).maxByOrNull { item ->
                val repeatedType = recent.count { it.tag == item.tag }
                val similarity = recent.maxOfOrNull { previous ->
                    val a = vectors[item] ?: features(item)
                    val b = vectors[previous] ?: features(previous)
                    val shared = a.count { it in b }
                    shared.toDouble() / (a.size + b.size - shared).coerceAtLeast(1)
                } ?: 0.0
                
                val base = if (explore) {
                    (popularity(item) * 1.3 + freshness(item) * 0.6 + quality(item) * 0.5) - if (item in seenItems) 1.5 else 0.0
                } else {
                    ranked[item] ?: 0.0
                }
                base - repeatedType * (if (explore) 0.4 else 0.22) - similarity * 0.4
            } ?: break
            
            result += next
            remaining.remove(next)
        }
        return result + remaining
    }
}
