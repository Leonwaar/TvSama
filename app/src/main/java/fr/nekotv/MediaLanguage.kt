package fr.nekotv

/** Original audio alone does not establish that French subtitles are available. */
internal fun mediaLanguage(label: String, subtitleLabels: List<String>): String = when {
    Regex("(?i)\\bvostfr\\b").containsMatchIn(label) -> "VOSTFR"
    Regex("(?i)\\b(vf|vff|vfq|truefrench|french|fr)\\b").containsMatchIn(label) -> "VF"
    subtitleLabels.any { Regex("(?i)french|français|francais|\\bfr\\b|vostfr").containsMatchIn(it) } -> "VOSTFR"
    else -> "UNKNOWN"
}
