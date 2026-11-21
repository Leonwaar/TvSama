package fr.nekotv

private val frenchSubtitle = Regex("(?i)french|français|francais|\\bfr\\b|vostfr")
private val explicitVostfr = Regex("(?i)\\bvostfr\\b|\\bvo[- ]?stfr\\b|\\bvost\\b")
private val explicitVf = Regex("(?i)\\b(vf|vff|vfq|truefrench|french|français|francais|fr)\\b")
private val originalVersion = Regex("(?i)\\b(vo|vost|original)\\b")
private val otherAudio = Regex("(?i)\\b(italian|italiano|italien|portuguese|português|portugues|portugais|german|deutsch|allemand|spanish|español|espanol|espagnol|english|anglais|japanese|japonais|korean|coréen|coreen|chinese|chinois|russian|russe|arabic|arabe|hindi|tamil|telugu)\\b")

/**
 * Classifies a published reader for the app's two French-oriented modes.
 *
 * A subtitle label alone never proves the audio language, and "VO" without a French
 * subtitle track is not VOSTFR. Anything that cannot be confirmed stays [UNKNOWN] so it
 * is filtered out instead of playing the original audio under a VF label.
 */
internal fun mediaLanguage(label: String, subtitleLabels: List<String>): String {
    val hasFrenchSubtitles = subtitleLabels.any { frenchSubtitle.containsMatchIn(it) }
    return when {
        explicitVostfr.containsMatchIn(label) -> "VOSTFR"
        explicitVf.containsMatchIn(label) -> "VF"
        hasFrenchSubtitles && (originalVersion.containsMatchIn(label) || otherAudio.containsMatchIn(label)) -> "VOSTFR"
        originalVersion.containsMatchIn(label) -> "OTHER"
        otherAudio.containsMatchIn(label) -> "OTHER"
        // French subtitles on an unlabeled reader are not enough: the audio could be French.
        else -> "UNKNOWN"
    }
}

/** Sources are only offered when their requested language is explicitly confirmed. */
internal fun acceptsMediaLanguage(requested: String, actual: String) =
    requested in listOf("VF", "VOSTFR") && actual == requested
