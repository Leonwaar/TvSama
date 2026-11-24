package fr.nekotv

import java.text.Normalizer
import java.util.Locale

object Normalize {
    private val combiningMarks = Regex("\\p{M}")
    private val wordBoundaries = Regex("[^\\p{L}\\p{N}']+")
    private val articles = setOf("le", "la", "les", "the", "a", "an", "un", "une", "des")

    private fun stripAccentsAndPunct(value: String): String {
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
        val withoutAccents = combiningMarks.replace(decomposed, "")
        return Regex("[^\\p{L}\\p{N}\\s']").replace(withoutAccents, " ")
    }

    fun normalizeQuery(value: String): String {
        val stripped = stripAccentsAndPunct(value).lowercase(Locale.ROOT)
        return wordBoundaries.split(stripped)
            .filter { it.isNotBlank() && !articles.contains(it) }
            .joinToString(" ")
    }

    fun normalizeTitle(value: String): String {
        return stripAccentsAndPunct(value).lowercase(Locale.ROOT)
    }

    fun tokens(value: String): Set<String> {
        val stripped = stripAccentsAndPunct(value).lowercase(Locale.ROOT)
        return wordBoundaries.split(stripped)
            .filter { it.length > 2 && !articles.contains(it) }
            .toSet()
    }

    fun cacheKey(value: String, maxLength: Int = 200): String {
        return normalizeQuery(value).take(maxLength)
    }
}
