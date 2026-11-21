package fr.nekotv

/** A single advance per episode, even while a replacement server is resolving. */
internal class EpisodeAdvance {
    var triggered = false
        private set
    private var postCreditsReached = false

    fun claimPostCredits(position: Long, duration: Long, segments: EpisodeSegments?, eligible: Boolean): Long? {
        val scene = boundedSegment(segments?.postCredits, duration, true) ?: return null
        if (position >= scene.start) { postCreditsReached = true; return null }
        if (!eligible || postCreditsReached) return null
        val outro = segments.skipSegments(duration).filter { it.ending && it.range.start < scene.start }.minByOrNull { it.range.start }?.range ?: return null
        if (position < outro.start || outro.start >= scene.start) return null
        postCreditsReached = true
        return scene.start
    }

    fun claim(position: Long, duration: Long, outro: IntroSegment?, eligible: Boolean): Boolean {
        if (triggered || !eligible || duration <= 0 || position < 0) return false
        // Déclencher dès le début de l'outro (générique de fin) pour passer à l'épisode suivant AVANT le générique
        val outroSegment = boundedSegment(outro, duration, ending = true)
        val threshold = outroSegment?.start ?: duration
        if (position < threshold) return false
        triggered = true
        return true
    }
}

internal fun boundedSegment(segment: IntroSegment?, duration: Long, ending: Boolean): IntroSegment? {
    if (segment == null || duration <= 0 || segment.start < 0 || segment.end <= segment.start ||
        segment.end != Long.MAX_VALUE && segment.end > duration + 15_000) return null
    if (ending && segment.start < duration / 2 || !ending && segment.end > minOf(duration / 2, 900_000)) return null
    return segment.copy(end = minOf(segment.end, duration)).takeIf { it.end > it.start }
}

internal fun showNextEpisodePrompt(position: Long, duration: Long, outro: IntroSegment?, postCredits: IntroSegment? = null): Boolean {
    if (duration < 300_000 || position < 0 || position >= duration) return false
    val scene = boundedSegment(postCredits, duration, true)
    if (scene != null && position < scene.end) return false
    val start = boundedSegment(outro, duration, true)?.start?.minus(30_000) ?: (duration - 120_000)
    return position >= start
}

internal fun EpisodeSegments?.skipSegments(duration: Long): List<SkipSegment> {
    if (this == null) return emptyList()
    return (listOfNotNull(intro?.let { SkipSegment("Passer le générique du début", it) },
        outro?.let { SkipSegment("Passer le générique de fin", it, true) }) + additional)
        .mapNotNull { part -> boundedSegment(part.range, duration, part.ending)?.let { range ->
            val scene = boundedSegment(postCredits, duration, true)
            when {
                part.ending && scene != null && range.start in scene.start until scene.end -> null
                part.ending && scene != null && range.start < scene.start -> part.copy(
                    label = "Passer à la scène après le générique", range = range.copy(end = minOf(range.end, scene.start)))
                else -> part.copy(range = range)
            }
        } }
        .distinctBy { "${it.range.start / 5_000}:${it.ending}" }.sortedBy { it.range.start }
}

internal fun EpisodeSegments?.autoOutro(duration: Long): IntroSegment? {
    if (this == null || duration <= 0) return null
    val lastStart = skipSegments(duration).filter { it.ending }.maxOfOrNull { it.range.start } ?: return null
    val start = maxOf(lastStart, boundedSegment(postCredits, duration, true)?.end ?: 0)
    return IntroSegment(start, duration).takeIf { start < duration }
}
