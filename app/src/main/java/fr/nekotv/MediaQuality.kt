package fr.nekotv

/** Quality declarations come from server labels; opaque media URLs contain unrelated digits. */
internal fun declaredQuality(label: String): String {
    fun has(value: String) = Regex("(?i)(?<![\\p{L}\\p{N}])(?:$value)(?![\\p{L}\\p{N}])").containsMatchIn(label)
    return when {
        has("2160p?|4k|uhd") -> "4K"
        has("1080p?|fhd") -> "1080p"
        has("720p?") -> "720p"
        has("480p?") -> "480p"
        else -> "Auto"
    }
}

internal fun actualQuality(width: Int, height: Int): String = when {
    width >= 3840 || height >= 2160 -> "4K"
    width >= 1920 || height >= 1080 -> "1080p"
    width >= 1280 || height >= 720 -> "720p"
    width >= 854 || height >= 480 -> "480p"
    width > 0 || height > 0 -> "${height}p"
    else -> "Détection…"
}
