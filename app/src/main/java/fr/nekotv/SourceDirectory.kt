package fr.nekotv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Discovery records are not playback adapters. Keep the stable slug to refresh rotating URLs. */
data class DirectorySource(val name: String, val slug: String, val url: String, val status: String) {
    val permanentUrl get() = "https://app.oustreamer.com/site/$slug"
}

object SourceDirectory {
    suspend fun loadFrench(): List<DirectorySource> = withContext(Dispatchers.IO) {
        val entries = mutableListOf<DirectorySource>()
        var page = 1
        var last = 1
        do {
            ensureActive()
            val connection = URL("https://app.oustreamer.com/api/sites?per_page=100&page=$page").openConnection() as HttpURLConnection
            val data = try {
                // Reduced timeouts for better responsiveness
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                check(connection.responseCode == 200) { "Annuaire indisponible (HTTP ${connection.responseCode})" }
                JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            } finally { connection.disconnect() }
            check(data.optBoolean("success")) { "Réponse de l’annuaire invalide" }
            last = data.optJSONObject("meta")?.optInt("last_page", 1)?.coerceIn(1, 50) ?: 1
            val items = data.getJSONArray("data")
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                if (item.optString("language") != "fr" || item.optString("category") !in setOf("films", "series", "anime", "iptv")) continue
                val url = item.optJSONObject("current_url")?.optString("url").orEmpty()
                if (!url.startsWith("https://")) continue
                entries += DirectorySource(item.getString("name"), item.getString("slug"), url, item.optString("status"))
            }
            page++
        } while (page <= last)
        entries.distinctBy { it.slug }
    }
}
