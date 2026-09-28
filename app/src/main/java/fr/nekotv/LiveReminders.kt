package fr.nekotv

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONObject

/** User-selected reminders are exact so Android cannot shift the requested ten-minute warning. */
object LiveReminders {
    private const val CHANNEL = "live_schedule_reminders"
    private const val PREFS = "tvsama_live_reminders"

    fun schedule(context: Context, id: String, title: String, startsAt: Long): Boolean {
        val notifications = context.getSystemService(NotificationManager::class.java) ?: return false
        if (Build.VERSION.SDK_INT >= 24 && !notifications.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = notifications.getNotificationChannel(CHANNEL)
            if (channel == null) {
                // Create channel if it doesn't exist
                notifications.createNotificationChannel(NotificationChannel(
                    CHANNEL, "Rappels de diffusion", NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Rappels dix minutes avant un direct suivi" })
            } else if (channel.importance == NotificationManager.IMPORTANCE_NONE) {
                // Update channel importance if it was set to none
                notifications.createNotificationChannel(NotificationChannel(
                    CHANNEL, channel.name, NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = channel.description
                })
                return false // Indicate that we fixed the issue and caller should retry
            }
        }
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return false
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return false
        if (Build.VERSION.SDK_INT >= 31 && !alarm.canScheduleExactAlarms()) return false
        val notificationAt = startsAt - 10 * 60_000L
        if (notificationAt <= System.currentTimeMillis()) return false
        val key = id.hashCode() and Int.MAX_VALUE
        val intent = Intent(context, LiveReminderReceiver::class.java).apply {
            action = "fr.nekotv.LIVE_REMINDER.$key"
            data = android.net.Uri.fromParts("tvsama-reminder", id, null)
            putExtra("event_title", title)
            putExtra("event_id", id)
        }
        val pending = PendingIntent.getBroadcast(context, key, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (Build.VERSION.SDK_INT >= 23) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, notificationAt, pending)
        else alarm.setExact(AlarmManager.RTC_WAKEUP, notificationAt, pending)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(id, JSONObject().put("title", title).put("startsAt", startsAt).toString()).apply()
        return true
    }

    fun restore(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all.forEach { (id, value) ->
            runCatching {
                val data = JSONObject(value as String)
                if (!schedule(context, id, data.getString("title"), data.getLong("startsAt"))) cancel(context, id)
            }
        }
    }

    fun cancel(context: Context, id: String) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val key = id.hashCode() and Int.MAX_VALUE
        val intent = Intent(context, LiveReminderReceiver::class.java).setAction("fr.nekotv.LIVE_REMINDER.$key").setData(android.net.Uri.fromParts("tvsama-reminder", id, null))
        PendingIntent.getBroadcast(context, key, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
            alarm.cancel(it); it.cancel()
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(id).apply()
    }

    fun has(context: Context, id: String) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(id)

    internal fun show(context: Context, id: String, title: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(
            CHANNEL, "Rappels de diffusion", NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "Rappels dix minutes avant un direct suivi" })
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).putExtra("open_live", true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, CHANNEL)
            else @Suppress("DEPRECATION") Notification.Builder(context)
        val notification = builder.setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Dans 10 minutes : $title")
            .setContentText("La diffusion que vous suivez commence bientôt.")
            .setCategory(Notification.CATEGORY_REMINDER).setAutoCancel(true).setContentIntent(open).build()
        if (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            runCatching { manager.notify(id, 0, notification) }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(id).apply()
    }
}

class LiveReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)) {
            LiveReminders.restore(context); return
        }
        LiveReminders.show(context, intent.getStringExtra("event_id").orEmpty(), intent.getStringExtra("event_title") ?: "Votre diffusion")
    }
}
