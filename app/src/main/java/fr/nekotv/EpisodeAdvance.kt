package fr.nekotv

/** A single advance per episode, even while a replacement server is resolving. */
internal class EpisodeAdvance {
    var triggered = false
        private set

    fun claim(position: Long, duration: Long, outro: IntroSegment?, eligible: Boolean): Boolean {
        if (triggered || !eligible || duration <= 0 || position < 0) return false
        val outroStart = outro?.takeIf { it.start > 0 && it.end > it.start && it.end <= duration }?.start
        // Short clips must play through rather than skip as soon as they start.
        val fallback = if (duration > 30_000) duration - 30_000 else duration
        val threshold = minOf(outroStart ?: fallback, fallback)
        if (position < threshold) return false
        triggered = true
        return true
    }
}
