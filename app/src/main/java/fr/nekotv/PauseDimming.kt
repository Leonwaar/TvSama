package fr.nekotv

/** Uses monotonic time; buffering is not a user pause and interactions restart the idle period. */
internal class PauseDimming(private val now: () -> Long = android.os.SystemClock::uptimeMillis) {
    private var pausedAt: Long? = null
    fun shouldDim(paused: Boolean, enabled: Boolean, lastInteraction: Long): Boolean {
        if (!paused || !enabled) { pausedAt = null; return false }
        val time = now()
        if (pausedAt == null) pausedAt = time
        return time - maxOf(pausedAt!!, lastInteraction) >= 30_000
    }
}
