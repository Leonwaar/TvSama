package fr.nekotv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SavedPlayback(val anime: Anime, val episodeId: String, val episodeTitle: String, val position: Long, val duration: Long)

class LibraryStore(context: Context) {
    private val prefs = context.getSharedPreferences("tvsama_library", 0)
    fun favorites(): List<Anime> = readArray("favorites").mapNotNull { runCatching { decodeAnime(it) }.getOrNull() }
    fun favorite(anime: Anime) = favorites().any { key(it) == key(anime) }
    fun toggle(anime: Anime) {
        val list = favorites()
        saveArray("favorites", if (favorite(anime)) list.filterNot { key(it) == key(anime) }.map(::encodeAnime) else (listOf(anime) + list).map(::encodeAnime))
    }
    fun history(): List<SavedPlayback> = readArray("history").mapNotNull { runCatching {
        SavedPlayback(decodeAnime(it.getJSONObject("anime")), it.optString("episode"), it.optString("episodeTitle"), it.optLong("position"), it.optLong("duration"))
    }.getOrNull() }
    fun progress(anime: Anime, episode: Episode?) = history().firstOrNull { key(it.anime) == key(anime) && it.episodeId == episode?.id.orEmpty() }?.position ?: 0L
    fun save(anime: Anime, episode: Episode?, position: Long, duration: Long) {
        if (position < 1000 || duration <= 0) return
        val entry = SavedPlayback(anime, episode?.id.orEmpty(), episode?.title ?: "Film", position, duration)
        val entries = (listOf(entry) + history().filterNot { key(it.anime) == key(anime) && it.episodeId == entry.episodeId }).take(60)
        saveArray("history", entries.map { JSONObject().put("anime", encodeAnime(it.anime)).put("episode", it.episodeId).put("episodeTitle", it.episodeTitle).put("position", it.position).put("duration", it.duration) })
    }
    fun clearHistory() { prefs.edit().remove("history").apply() }
    fun removeHistory(anime: Anime, episodeId: String) {
        saveArray("history", history().filterNot { key(it.anime) == key(anime) && it.episodeId == episodeId }.map {
            JSONObject().put("anime", encodeAnime(it.anime)).put("episode", it.episodeId).put("episodeTitle", it.episodeTitle)
                .put("position", it.position).put("duration", it.duration)
        })
    }
    fun language(): String = prefs.getString("language", "Toutes") ?: "Toutes"
    fun setLanguage(value: String) { prefs.edit().putString("language", value).apply() }
    fun autoplay(): Boolean = prefs.getBoolean("autoplay", true)
    fun setAutoplay(value: Boolean) { prefs.edit().putBoolean("autoplay", value).apply() }
    private fun key(a: Anime) = a.title.lowercase().trim() + "|" + a.year + "|" + a.tag
    private fun readArray(key: String): List<JSONObject> = runCatching {
        val array = JSONArray(prefs.getString(key, "[]")); (0 until array.length()).map { array.getJSONObject(it) }
    }.getOrDefault(emptyList())
    private fun saveArray(key: String, items: List<JSONObject>) { prefs.edit().putString(key, JSONArray(items).toString()).apply() }
    private fun encodeAnime(a: Anime): JSONObject = JSONObject().put("title", a.title).put("tag", a.tag).put("id", a.id)
        .put("poster", a.poster).put("description", a.description).put("year", a.year).put("provider", a.provider).put("banner", a.banner)
        .put("genres", JSONArray(a.genres)).put("imdbId", a.imdbId).put("references", JSONArray().apply {
            a.references.forEach { put(JSONObject().put("provider", it.provider).put("id", it.id).put("tag", it.tag)) }
        })
    private fun decodeAnime(o: JSONObject): Anime {
        val refs = o.optJSONArray("references")?.let { array -> (0 until array.length()).map { index ->
            val ref = array.getJSONObject(index)
            MediaReference(ref.optString("provider"), ref.optString("id"), ref.optString("tag"))
        } } ?: emptyList()
        val genres = o.optJSONArray("genres")?.let { array -> (0 until array.length()).map { array.getString(it) } } ?: emptyList()
        return Anime(title = o.getString("title"), tag = o.optString("tag"), episodes = emptyList(), id = o.optString("id"),
            poster = o.optString("poster"), description = o.optString("description"), year = o.optInt("year").takeIf { it > 0 },
            genres = genres, provider = o.optString("provider"), banner = o.optString("banner"), references = refs,
            imdbId = o.optString("imdbId").takeIf { it.isNotBlank() })
    }
}
