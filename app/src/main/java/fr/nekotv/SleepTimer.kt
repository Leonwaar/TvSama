package fr.nekotv

import android.content.Context

/** Stores the selected duration and the deadline for the current lecture. */
class SleepTimer(context: Context, private val now: () -> Long = System::currentTimeMillis) {
    private val prefs = context.getSharedPreferences("tvsama_sleep_timer", Context.MODE_PRIVATE)
    private val settings = context.getSharedPreferences("tvsama_settings", Context.MODE_PRIVATE)
    fun minutes() = settings.getInt("sleep_minutes", 0).takeIf { it in options } ?: 0
    fun configure(minutes: Int) {
        require(minutes == 0 || minutes in options)
        settings.edit().putInt("sleep_minutes", minutes).apply()
        prefs.edit().remove("deadline").apply()
    }
    fun deadline(): Long = prefs.getLong("deadline", 0)
    fun begin(): Long {
        if (minutes() == 0) return 0
        if (deadline() == 0L) restart()
        return deadline()
    }
    fun restart(): Long {
        val deadline = if (minutes() == 0) 0 else now() + minutes() * 60_000L
        prefs.edit().putLong("deadline", deadline).apply()
        return deadline
    }
    fun expired() = deadline() > 0 && now() >= deadline()
    companion object {
        val options = listOf(60, 90, 120, 150, 180)
        fun label(minutes: Int) = "${minutes / 60} h" + if (minutes % 60 == 0) "" else " 30"
    }
}
