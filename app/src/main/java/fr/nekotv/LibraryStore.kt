package fr.nekotv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SavedPlayback(val anime: Anime, val episodeId: String, val episodeTitle: String, val position: Long, val duration: Long, val season: Int = 0, val number: Int = 0)

class LibraryStore(context: Context) {
    private val prefs = context.getSharedPreferences("tvsama_library", 0)
    fun favorites(): List<Anime> = readArray("favorites").mapNotNull { runCatching { decodeAnime(it) }.getOrNull() }
    fun favorite(anime: Anime) = favorites().any { sameFavorite(it, anime) }
    fun setFavorite(anime: Anime, saved: Boolean) {
        synchronized(favoritesLock) {
            val remaining = favorites().filterNot { sameFavorite(it, anime) }
            saveArray("favorites", (if (saved) listOf(anime) + remaining else remaining).map(::encodeAnime))
        }
    }
    fun toggle(anime: Anime) {
        synchronized(favoritesLock) { setFavorite(anime, !favorite(anime)) }
    }
    companion object {
        private val favoritesLock = Any()

        /** Metadata may change between a catalogue card and its loaded details. */
        fun sameFavorite(a: Anime, b: Anime): Boolean {
            if (a.tag != b.tag) return false
            fun imdb(value: String?) = value?.takeIf { it.matches(Regex("tt[0-9]+")) }
            val firstImdb = imdb(a.imdbId)
            val secondImdb = imdb(b.imdbId)
            if (firstImdb != null && secondImdb != null) return firstImdb == secondImdb
            fun references(item: Anime) = (item.references + MediaReference(item.provider, item.id, item.tag))
                .filter { it.provider.isNotBlank() && it.id.isNotBlank() }
                .map { ref ->
                    val id = runCatching {
                        val uri = java.net.URI(ref.id)
                        if (uri.host != null) uri.rawPath.trimEnd('/') + (uri.rawQuery?.let { "?$it" } ?: "") +
                            (uri.rawFragment?.let { "#$it" } ?: "") else ref.id
                    }.getOrDefault(ref.id)
                    ref.provider to id
                }.toSet()
            if (references(a).intersect(references(b)).isNotEmpty()) return true
            val title = CatalogIdentity.title(a.title)
            return title.isNotBlank() && title == CatalogIdentity.title(b.title) && a.year == b.year
        }
    }
    fun history(): List<SavedPlayback> = readArray("history").mapNotNull { runCatching {
        SavedPlayback(decodeAnime(it.getJSONObject("anime")), it.optString("episode"), it.optString("episodeTitle"), it.optLong("position"), it.optLong("duration"), it.optInt("season"), it.optInt("number"))
    }.getOrNull() }
    fun playback(anime: Anime, episode: Episode?) = history().firstOrNull {
        key(it.anime) == key(anime) && (it.episodeId == episode?.id.orEmpty() ||
            (episode != null && it.season == episode.seasonNumber && it.number == episode.number))
    }
    fun progress(anime: Anime, episode: Episode?) = playback(anime, episode)?.let { if (finished(it)) 0L else it.position } ?: 0L
    fun fraction(anime: Anime, episode: Episode): Float = playback(anime, episode)?.let {
        if (finished(it)) 1f else (it.position.toFloat() / it.duration.coerceAtLeast(1)).coerceIn(0f, 1f)
    } ?: 0f
    fun finished(entry: SavedPlayback) = entry.duration > 0 && entry.duration - entry.position <= 30_000
    fun resumeEpisode(anime: Anime): Episode? {
        val last = history().firstOrNull { key(it.anime) == key(anime) } ?: return anime.episodes.firstOrNull()
        val ordered = anime.episodes.distinctBy { it.seasonNumber to it.number }
        val index = ordered.indexOfFirst { it.id == last.episodeId || (it.seasonNumber == last.season && it.number == last.number) }
        return if (index < 0) anime.episodes.firstOrNull() else
            ordered.getOrNull(index + if (finished(last)) 1 else 0) ?: ordered[index]
    }
    fun continuing(): List<SavedPlayback> = history().distinctBy { key(it.anime) }
        .filterNot { key(it.anime) in prefs.getStringSet("hidden_resume", emptySet()).orEmpty() }
        .map { entry ->
            if (!finished(entry)) entry else {
                val next = resumeEpisode(entry.anime)
                if (next != null && next.id != entry.episodeId) entry.copy(episodeId = next.id,
                    episodeTitle = next.title, position = 0, duration = 0, season = next.seasonNumber, number = next.number)
                else entry
            }
        }
    fun hideResume(anime: Anime) { prefs.edit().putStringSet("hidden_resume",
        prefs.getStringSet("hidden_resume", emptySet()).orEmpty() + key(anime)).apply() }
    fun searches() = readArray("searches").map { it.optString("query") }.filter { it.isNotBlank() }
    fun rememberSearch(query: String) {
        if (query.isBlank()) return
        saveArray("searches", (listOf(query.trim()) + searches().filterNot { it.equals(query.trim(), true) }).take(12).map { JSONObject().put("query", it) })
    }
    fun save(anime: Anime, episode: Episode?, position: Long, duration: Long) {
        if (position < 1000 || duration <= 0) return
        val catalogue = encodeAnime(anime).getJSONArray("episodes").toString()
        val catalogueKey = "episodes|${key(anime)}"
        if (prefs.getString(catalogueKey, null) != catalogue) prefs.edit().putString(catalogueKey, catalogue).apply()
        val entry = SavedPlayback(anime, episode?.id.orEmpty(), episode?.title ?: "Film", position.coerceAtMost(duration), duration, episode?.seasonNumber ?: 0, episode?.number ?: 0)
        prefs.edit().putStringSet("hidden_resume", prefs.getStringSet("hidden_resume", emptySet()).orEmpty() - key(anime)).apply()
        val entries = (listOf(entry) + history().filterNot { key(it.anime) == key(anime) && (it.episodeId == entry.episodeId || (it.season == entry.season && it.number == entry.number)) }).take(1000)
        saveArray("history", entries.map { JSONObject().put("anime", encodeAnime(it.anime.copy(episodes = emptyList()))).put("episode", it.episodeId).put("episodeTitle", it.episodeTitle).put("position", it.position).put("duration", it.duration).put("season", it.season).put("number", it.number) })
    }
    fun clearHistory() { prefs.edit().remove("history").remove("hidden_resume").apply() }
    fun removeHistory(anime: Anime, episodeId: String) {
        saveArray("history", history().filterNot { key(it.anime) == key(anime) && it.episodeId == episodeId }.map {
            JSONObject().put("anime", encodeAnime(it.anime.copy(episodes = emptyList()))).put("episode", it.episodeId).put("episodeTitle", it.episodeTitle)
                .put("position", it.position).put("duration", it.duration).put("season", it.season).put("number", it.number)
        })
    }
    fun language(): String = prefs.getString("language", "VF")?.takeIf { it in listOf("VF", "VOSTFR") } ?: "VF"
    fun setLanguage(value: String) { prefs.edit().putString("language", value).apply() }
    fun autoplay(): Boolean = prefs.getBoolean("autoplay", true)
    fun setAutoplay(value: Boolean) { prefs.edit().putBoolean("autoplay", value).apply() }
    private fun key(a: Anime) = CatalogIdentity.title(a.title) + "|" + a.year + "|" + a.tag
    private fun readArray(key: String): List<JSONObject> = runCatching {
        val array = JSONArray(prefs.getString(key, "[]")); (0 until array.length()).map { array.getJSONObject(it) }
    }.getOrDefault(emptyList())
    private fun saveArray(key: String, items: List<JSONObject>) { prefs.edit().putString(key, JSONArray(items).toString()).apply() }
    private fun encodeAnime(a: Anime): JSONObject = JSONObject().put("title", a.title).put("tag", a.tag).put("id", a.id)
        .put("poster", a.poster).put("description", a.description).put("year", a.year).put("provider", a.provider).put("banner", a.banner)
        .put("episodes", JSONArray().apply { a.episodes.forEach { put(JSONObject().put("id", it.id).put("title", it.title).put("season", it.seasonNumber).put("number", it.number).put("language", it.language)) } })
        .put("genres", JSONArray(a.genres)).put("imdbId", a.imdbId).put("references", JSONArray().apply {
            a.references.forEach { put(JSONObject().put("provider", it.provider).put("id", it.id).put("tag", it.tag)) }
        })
    private fun decodeAnime(o: JSONObject): Anime {
        val refs = o.optJSONArray("references")?.let { array -> (0 until array.length()).map { index ->
            val ref = array.getJSONObject(index)
            MediaReference(ref.optString("provider"), ref.optString("id"), ref.optString("tag"))
        } } ?: emptyList()
        val genres = o.optJSONArray("genres")?.let { array -> (0 until array.length()).map { array.getString(it) } } ?: emptyList()
        return Anime(title = o.getString("title"), tag = o.optString("tag"), episodes = (o.optJSONArray("episodes")?.takeIf { it.length() > 0 } ?: runCatching {
                JSONArray(prefs.getString("episodes|${CatalogIdentity.title(o.optString("title"))}|${o.optInt("year").takeIf { it > 0 }}|${o.optString("tag")}", "[]"))
            }.getOrNull())?.let { array -> (0 until array.length()).map { i ->
                val e = array.getJSONObject(i)
                Episode(e.optString("title"), "", emptyList(), e.optString("id"), e.optInt("number", 1), e.optInt("season", 1), language = e.optString("language", "UNKNOWN"))
            } }.orEmpty(), id = o.optString("id"),
            poster = o.optString("poster"), description = o.optString("description"), year = o.optInt("year").takeIf { it > 0 },
            genres = genres, provider = o.optString("provider"), banner = o.optString("banner"), references = refs,
            imdbId = o.optString("imdbId").takeIf { it.isNotBlank() })
    }
}
