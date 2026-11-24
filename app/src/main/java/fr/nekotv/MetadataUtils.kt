package fr.nekotv

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Central resolver for content synopses to ensure they are always in French.
 */
object SynopsisResolver {
    private val ENGLISH_COMMON_WORDS = setOf("the", "and", "with", "this", "that", "from", "which", "is", "an", "english", "of", "to", "in", "for")
    private val FRENCH_COMMON_WORDS = setOf("le", "la", "les", "un", "une", "des", "et", "est", "dans", "ceci", "qui", "pour", "en", "du", "au", "aux", "français")

    /**
     * Resolves the best French synopsis for the given anime.
     * Priority:
     * 1. explicit fr-FR (already in description if TMDB was used with fr-FR)
     * 2. other fr variants
     * 3. other merged sources (if they have fr)
     * 4. machine translation (placeholder for now)
     * 5. Fallback message
     */
    fun resolveOverview(anime: Anime): ResolvedOverview {
        // 1. Primary description (already prioritized during merge/enrichment)
        val primary = anime.description.trim()
        if (primary.isNotBlank() && isFrench(primary)) {
            return ResolvedOverview(primary, "native")
        }

        // 2. Fallback to other providers' descriptions if they have French
        val bestSource = anime.descriptions.entries
            .filter { it.value.isNotBlank() && isFrench(it.value) }
            .firstOrNull()
        
        if (bestSource != null) {
            return ResolvedOverview(bestSource.value, "native")
        }

        // 3. Machine translation fallback (triggered during enrichment in real impl, 
        // but provided here for API consistency)
        if (anime.description.isNotBlank()) {
            // In a production env, this would be a cached result from a translation service
            return ResolvedOverview("Traduction automatique de : ${anime.description}", "machine")
        }

        return ResolvedOverview("Le synopsis n'est pas disponible en français.", "none")
    }

    private fun isFrench(text: String): Boolean {
        if (text.isBlank()) return false
        val words = text.lowercase().split(Regex("[^\\p{L}]+")).filter { it.isNotBlank() }.toSet()
        val hasEnglish = words.any { it in ENGLISH_COMMON_WORDS }
        val hasFrench = words.any { it in FRENCH_COMMON_WORDS }
        
        if (hasEnglish) return false
        return hasFrench || words.isNotEmpty()
    }

    data class ResolvedOverview(val text: String, val source: String)
}

/**
 * Standardized release date formatting.
 */
object ReleaseInfoFormatter {
    private val DATE_FORMAT_FULL = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE)
    private val DATE_FORMAT_YEAR = DateTimeFormatter.ofPattern("yyyy", Locale.FRANCE)

    fun formatReleaseInfo(anime: Anime, variant: String = "full"): String {
        val start = parseDate(anime.firstAired)
        val end = parseDate(anime.endDate ?: anime.lastAired)
        val status = anime.status

        if (anime.tag == "Film") {
            return if (start != null) {
                if (anime.firstAired?.length == 4) {
                    start.format(DATE_FORMAT_YEAR)
                } else {
                    "Sortie le ${start.format(DATE_FORMAT_FULL)}"
                }
            } else {
                "Date non communiquée"
            }
        }

        // Series logic
        return when {
            start == null -> "Date non communiquée"
            status == "annulee" -> {
                "${start.format(DATE_FORMAT_YEAR)} · Annulée"
            }
            end == null && status == "en_cours" -> {
                "${start.format(DATE_FORMAT_YEAR)} – en cours"
            }
            end != null -> {
                if (variant == "compact") {
                    "${start.format(DATE_FORMAT_YEAR)} – ${end.format(DATE_FORMAT_YEAR)}"
                } else {
                    "Du ${start.format(DATE_FORMAT_FULL)} au ${end.format(DATE_FORMAT_FULL)}"
                }
            }
            status == "a_venir" -> {
                if (start != null) "Prévue le ${start.format(DATE_FORMAT_FULL)}" else "À venir"
            }
            else -> "${start.format(DATE_FORMAT_YEAR)}"
        }
    }

    private fun parseDate(date: String?): LocalDate? {
        if (date == null || date.isBlank()) return null
        return try {
            if (date.length == 4) LocalDate.parse(date + "-01-01") else LocalDate.parse(date)
        } catch (e: Exception) {
            null
        }
    }
}
