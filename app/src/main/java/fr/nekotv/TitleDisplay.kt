package fr.nekotv

/**
 * True when a title can be shown with Latin letters only.
 * Japanese (kanji/kana), Korean, Chinese, Cyrillic, Arabic… return false so they are
 * never displayed raw: the catalogue always keeps a romanised/Latin variant.
 */
internal fun isLatinTitle(value: String): Boolean {
    val letters = value.filter { it.isLetter() }
    if (letters.isEmpty()) return true
    return letters.none { ch ->
        val code = ch.code
        // Greek/Cyrillic/Hebrew/Arabic/Indic/Thai, kana/CJK/fullwidth, hangul syllables.
        // Latin Extended Additional (0x1E00–0x1EFF) stays allowed for accented titles.
        code in 0x0370..0x1DFF || code in 0x1F00..0xD7FF || code in 0xF900..0xFFFF
    }
}

/** A Latin fallback among the aliases, or the canonical English title when known. */
private fun latinFallback(anime: Anime): String? {
    // Gson may deserialize old cached Anime objects without fields introduced later;
    // Kotlin's non-null type is not enforced when Gson bypasses the constructor.
    anime.aliases.orEmpty().firstOrNull { it.isNotBlank() && isLatinTitle(it) }?.let { return it }
    return TitleAliases.canonical(anime.title).takeIf { it.isNotBlank() && isLatinTitle(it) }
}

/**
 * Title in the selected version's language:
 * - VF → French title when known, else the catalogue title (French providers are usual);
 * - VOSTFR → original title, romanised for Japanese works.
 * A Japanese/foreign script is never shown: a Latin alias is used instead.
 */
internal fun Anime.displayTitle(language: String): String {
    val french = language.equals("VF", ignoreCase = true)
    val order = if (french) listOf("fr", "original", "en", "romaji", "ja")
        else listOf("romaji", "original", "en", "ja", "fr")
    (localizedTitles ?: emptyMap()).let { localized ->
        order.firstNotNullOfOrNull { key ->
            localized[key]?.trim()?.takeIf { it.isNotBlank() && isLatinTitle(it) }
        }?.let { return it }
    }
    if (title.isNotBlank() && isLatinTitle(title)) return title
    return latinFallback(this) ?: title
}

/** Merges language-tagged titles without losing existing variants. */
internal fun Map<String, String>.mergeTitles(extra: Map<String, String?>): Map<String, String> {
    val merged = toMutableMap()
    extra.forEach { (key, value) -> value?.trim()?.takeIf(String::isNotBlank)?.let { merged[key] = it } }
    return merged
}
