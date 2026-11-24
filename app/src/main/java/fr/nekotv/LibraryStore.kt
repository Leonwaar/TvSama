package fr.nekotv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SavedPlayback(val anime: Anime, val episodeId: String, val episodeTitle: String, val position: Long, val duration: Long, val season: Int = 0, val number: Int = 0, val updatedAt: Long = 0)

class LibraryStore(context: Context) {
    private val prefs = context.getSharedPreferences("tvsama_library", 0)
    @Volatile private var historySnapshot: Pair<String, List<SavedPlayback>>? = null
    fun searchMatches(): List<Anime> = readArray("search_matches").mapNotNull { runCatching { decodeAnime(it) }.getOrNull() }
    fun rememberSearchMatches(query: String, matches: List<Anime>) {
        val key = CatalogIdentity.title(query)
        if (key.length < 2 || matches.isEmpty()) return
        val retained = readArray("search_matches").filterNot { it.optString("query") == key }
        saveArray("search_matches", (matches.take(3).map { encodeAnime(it).put("query", key) } + retained).take(60))
    }
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
        private val writes = java.util.concurrent.Executors.newSingleThreadExecutor { work ->
            Thread(work, "TvSama-library").apply { isDaemon = true }
        }

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
    fun history(): List<SavedPlayback> = readHistory("history")
    private fun recordCompletion(entry: SavedPlayback) {
        if (!finished(entry) || entry.anime.tag !in listOf("Film", "Série")) return
        val name = "finished|${key(entry.anime)}"
        val data = runCatching { JSONObject(prefs.getString(name, "{}").orEmpty()) }.getOrDefault(JSONObject())
        val episodes = data.optJSONArray("episodes") ?: JSONArray()
        val episodeKey = "${entry.season}:${entry.number}"
        if (data.has("anime") && (entry.anime.tag == "Film" || (0 until episodes.length()).any { episodes.optString(it) == episodeKey })) return
        if (entry.anime.tag == "Série") episodes.put(episodeKey)
        data.put("anime", encodeAnime(entry.anime.copy(episodes = emptyList())))
            .put("episodes", episodes).put("updatedAt", entry.updatedAt)
        prefs.edit().putString(name, data.toString()).apply()
    }
    fun completedContent(): List<Anime> = synchronized(favoritesLock) {
        if (!prefs.getBoolean("finished_migrated", false)) {
            history().filter(::finished).forEach(::recordCompletion)
            prefs.edit().putBoolean("finished_migrated", true).apply()
        }
        prefs.all.keys.filter { it.startsWith("finished|") }.mapNotNull { name -> runCatching {
            val data = JSONObject(prefs.getString(name, "{}").orEmpty())
            val anime = decodeAnime(data.getJSONObject("anime"))
            val watched = data.getJSONArray("episodes").let { list -> (0 until list.length()).map { list.getString(it) }.toSet() }
            val episodes = anime.episodes.filter { it.seasonNumber > 0 }.distinctBy { it.seasonNumber to it.number }
            if (anime.tag == "Film" || episodes.isNotEmpty() && episodes.all { "${it.seasonNumber}:${it.number}" in watched })
                data.optLong("updatedAt") to anime else null
        }.getOrNull() }.sortedByDescending { it.first }.map { it.second }
    }
    private fun readHistory(name: String): List<SavedPlayback> {
        val raw = prefs.getString(name, "[]").orEmpty()
        if (name == "history") historySnapshot?.takeIf { it.first == raw }?.let { return it.second }
        val result = runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index -> runCatching {
                val entry = array.getJSONObject(index)
                SavedPlayback(decodeAnime(entry.getJSONObject("anime"), loadSavedEpisodes = false),
                    entry.optString("episode"), entry.optString("episodeTitle"), entry.optLong("position"),
                    entry.optLong("duration"), entry.optInt("season"), entry.optInt("number"), entry.optLong("updatedAt"))
            }.getOrNull() }
        }.getOrDefault(emptyList())
        if (name == "history") historySnapshot = raw to result
        return result
    }
    private fun withEpisodes(anime: Anime): Anime = if (anime.episodes.isNotEmpty()) anime else decodeAnime(encodeAnime(anime))
    fun playback(anime: Anime, episode: Episode?) = history().firstOrNull {
        sameFavorite(it.anime, anime) && (anime.tag == "Film" || it.episodeId == episode?.id.orEmpty() ||
            (episode != null && it.season == episode.seasonNumber && it.number == episode.number))
    }
    fun progress(anime: Anime, episode: Episode?) = playback(anime, episode)?.let { if (finished(it)) 0L else it.position } ?: 0L
    fun fraction(anime: Anime, episode: Episode): Float = playback(anime, episode)?.let {
        if (finished(it)) 1f else (it.position.toFloat() / it.duration.coerceAtLeast(1)).coerceIn(0f, 1f)
    } ?: 0f
    fun finished(entry: SavedPlayback) = entry.duration > 0 && entry.duration - entry.position <= 30_000
    fun resumeEpisode(anime: Anime): Episode? {
        val last = history().firstOrNull { sameFavorite(it.anime, anime) } ?: return anime.firstEpisode()
        return resumeEpisode(withEpisodes(anime), last)
    }
    private fun resumeEpisode(anime: Anime, last: SavedPlayback): Episode? {
        val ordered = anime.episodes.distinctBy { it.seasonNumber to it.number }
        val index = ordered.indexOfFirst { it.id == last.episodeId || (it.seasonNumber == last.season && it.number == last.number) }
        return if (index < 0) anime.firstEpisode() else
            ordered.getOrNull(index + if (finished(last)) 1 else 0) ?: ordered[index]
    }
    fun continuing(): List<SavedPlayback> {
        val hidden = hiddenResumeTimes()
        val legacyHidden = prefs.getStringSet("hidden_resume", emptySet()).orEmpty()
        return history().distinctBy { key(it.anime) }
        .filterNot { key(it.anime) in legacyHidden || (hidden[key(it.anime)] ?: -1) >= it.updatedAt }
        .map { saved ->
            val entry = saved.copy(anime = withEpisodes(saved.anime))
            if (!finished(entry)) entry else {
                val next = resumeEpisode(entry.anime, entry)
                if (next != null && next.id != entry.episodeId) entry.copy(episodeId = next.id,
                    episodeTitle = next.title, position = 0, duration = 0, season = next.seasonNumber, number = next.number)
                else entry
            }
        }
    }
    fun hiddenResumeTimes(): Map<String, Long> {
        val data = runCatching { JSONObject(prefs.getString("hidden_resume_times", "{}") ?: "{}") }.getOrDefault(JSONObject())
        return data.keys().asSequence().associateWith { data.optLong(it) }
    }
    fun mergeHiddenResume(incoming: Map<String, Long>) = synchronized(favoritesLock) {
        val merged = hiddenResumeTimes().toMutableMap()
        incoming.forEach { (key, time) -> merged[key] = maxOf(merged[key] ?: 0, time) }
        prefs.edit().putString("hidden_resume_times", JSONObject(merged.toMap()).toString()).apply()
    }
    fun hideResume(anime: Anime) { mergeHiddenResume(mapOf(key(anime) to System.currentTimeMillis())) }
    fun searches() = readArray("searches").map { it.optString("query") }.filter { it.isNotBlank() }
    fun rememberSearch(query: String) {
        if (query.isBlank()) return
        val trimmed = query.trim()
        val current = searches().filterNot { it.equals(trimmed, true) }
        saveArray("searches", (listOf(trimmed) + current).take(12).map { JSONObject().put("query", it) })
    }

    fun removeSearch(query: String) {
        if (query.isBlank()) return
        val trimmed = query.trim()
        saveArray("searches", searches().filterNot { it.equals(trimmed, true) }.map { JSONObject().put("query", it) })
    }
    fun saveAsync(anime: Anime, episode: Episode?, position: Long, duration: Long): java.util.concurrent.Future<*>? {
        if (position < 1000 || duration <= 0) return null
        val requestedAt = System.currentTimeMillis()
        return writes.submit {
            try { synchronized(favoritesLock) {
                if (requestedAt <= prefs.getLong("history_cleared_at", 0) ||
                    requestedAt <= (hiddenResumeTimes()[key(anime)] ?: 0)) return@submit
                save(anime, episode, position, duration)
            } }
            catch (e: Exception) { android.util.Log.e("TvSamaLibrary", "Progression non enregistrée : ${e.javaClass.simpleName}") }
        }
    }
    fun save(anime: Anime, episode: Episode?, position: Long, duration: Long) = synchronized(favoritesLock) {
        if (position < 1000 || duration <= 0) return@synchronized
        val catalogue = encodeAnime(anime).getJSONArray("episodes").toString()
        val catalogueKey = "episodes|${key(anime)}"
        if (prefs.getString(catalogueKey, null) != catalogue) prefs.edit().putString(catalogueKey, catalogue).apply()
        val entry = SavedPlayback(anime, episode?.id.orEmpty(), if (anime.tag == "Film") "Film" else episode?.title.orEmpty(), position.coerceAtMost(duration), duration, if (anime.tag == "Film") 0 else episode?.seasonNumber ?: 0, if (anime.tag == "Film") 0 else episode?.number ?: 0, maxOf(System.currentTimeMillis(), (hiddenResumeTimes()[key(anime)] ?: 0) + 1))
        recordCompletion(entry)
        prefs.edit().putStringSet("hidden_resume", prefs.getStringSet("hidden_resume", emptySet()).orEmpty() - key(anime)).apply()
        val entries = (listOf(entry) + history().filterNot { sameFavorite(it.anime, anime) && (anime.tag == "Film" || it.episodeId == entry.episodeId || (it.season == entry.season && it.number == entry.number)) }).take(1000)
        saveArray("history", entries.map(::encodePlayback))
    }
    fun mergeHistory(incoming: List<SavedPlayback>) {
        synchronized(favoritesLock) {
            val clearedAt = prefs.getLong("history_cleared_at", 0)
            val merged = (incoming + history()).filter { it.position >= 0 && it.duration > 0 && (clearedAt == 0L || it.updatedAt > clearedAt) }
                .sortedByDescending { it.updatedAt }.distinctBy { key(it.anime) + "|${it.season}|${it.number}|${it.episodeId}" }.take(1000)
            merged.forEach { entry ->
                if (entry.anime.episodes.isNotEmpty()) prefs.edit().putString("episodes|${key(entry.anime)}", encodeAnime(entry.anime).getJSONArray("episodes").toString()).apply()
                recordCompletion(entry)
            }
            saveArray("history", merged.map(::encodePlayback))
        }
    }
    fun hasHistoryBackup() = readHistory("history_backup").isNotEmpty()
    fun clearHistory() = synchronized(favoritesLock) {
        val entries = history()
        if (entries.isEmpty()) return@synchronized
        val hidden = hiddenResumeTimes().toMutableMap()
        val now = System.currentTimeMillis()
        entries.forEach { hidden[key(it.anime)] = maxOf(hidden[key(it.anime)] ?: 0, now) }
        check(prefs.edit()
            .putString("history_backup", prefs.getString("history", "[]"))
            .putString("history_backup_hidden_times", prefs.getString("hidden_resume_times", "{}"))
            .putStringSet("history_backup_hidden", prefs.getStringSet("hidden_resume", emptySet()).orEmpty())
            .putString("hidden_resume_times", JSONObject(hidden.toMap()).toString())
            .putLong("history_cleared_at", now)
            .remove("history").remove("hidden_resume").commit()) { "Historique non effacé : sauvegarde impossible" }
    }
    fun restoreHistory() = synchronized(favoritesLock) {
        val backup = readHistory("history_backup")
        if (backup.isEmpty()) return@synchronized
        val keys = backup.map { key(it.anime) }.toSet()
        val previous = runCatching { JSONObject(prefs.getString("history_backup_hidden_times", "{}") ?: "{}") }.getOrDefault(JSONObject())
        val newlyWatched = history().map { key(it.anime) }.toSet()
        val previouslyHidden = backup.distinctBy { key(it.anime) }.filter {
            key(it.anime) in prefs.getStringSet("history_backup_hidden", emptySet()).orEmpty() ||
                (previous.optLong(key(it.anime), -1) >= it.updatedAt)
        }.map { key(it.anime) }.toSet() - newlyWatched
        val restoredAt = maxOf(System.currentTimeMillis(), (hiddenResumeTimes().values.maxOrNull() ?: 0) + 1)
        check(prefs.edit().remove("history_cleared_at").commit()) { "Restauration de l’historique impossible" }
        mergeHistory(backup)
        val merged = history()
        // Recovery is a new mutation so a peer's old deletion cannot hide it again.
        saveArray("history", merged.mapIndexed { index, entry ->
            encodePlayback(if (key(entry.anime) in keys && key(entry.anime) !in previouslyHidden)
                entry.copy(updatedAt = restoredAt + merged.size - index) else entry)
        })
        val hidden = hiddenResumeTimes().filterKeys { it !in keys }.toMutableMap()
        keys.forEach { if (previous.has(it)) hidden[it] = previous.optLong(it) }
        val hiddenCards = prefs.getStringSet("hidden_resume", emptySet()).orEmpty().filterNot { it in keys }.toSet() +
            previouslyHidden
        check(prefs.edit().putString("hidden_resume_times", JSONObject(hidden.toMap()).toString())
            .putStringSet("hidden_resume", hiddenCards)
            .remove("history_backup").remove("history_backup_hidden_times").remove("history_backup_hidden").commit()) {
            "Restauration de l’historique incomplète"
        }
    }
    fun removeHistory(anime: Anime, episodeId: String) {
        saveArray("history", history().filterNot { key(it.anime) == key(anime) && it.episodeId == episodeId }.map(::encodePlayback))
    }
    fun language(): String = when (prefs.getString("language", "VF")) {
        "VOSTFR" -> "VOSTFR"
        else -> "VF"
    }
    fun setLanguage(value: String) { if (value in listOf("VF", "VOSTFR")) prefs.edit().putString("language", value).apply() }
    fun autoplay(): Boolean = prefs.getBoolean("autoplay", true)
    fun setAutoplay(value: Boolean) { prefs.edit().putBoolean("autoplay", value).apply() }
    private fun key(a: Anime) = CatalogIdentity.title(a.title) + "|" + a.year + "|" + a.tag
    private fun readArray(key: String): List<JSONObject> = runCatching {
        val array = JSONArray(prefs.getString(key, "[]"))
        (0 until array.length()).mapNotNull { index -> runCatching { array.getJSONObject(index) }.getOrNull() }
    }.getOrDefault(emptyList())
    private fun saveArray(key: String, items: List<JSONObject>) { prefs.edit().putString(key, JSONArray(items).toString()).apply() }
    private fun encodePlayback(entry: SavedPlayback): JSONObject = JSONObject().put("anime", encodeAnime(entry.anime.copy(episodes = emptyList())))
        .put("episode", entry.episodeId).put("episodeTitle", entry.episodeTitle).put("position", entry.position)
        .put("duration", entry.duration).put("season", entry.season).put("number", entry.number).put("updatedAt", entry.updatedAt)
    private fun encodeAnime(a: Anime): JSONObject = JSONObject().put("title", a.title).put("tag", a.tag).put("id", a.id)
        .put("isLive", a.isLive).put("tmdbId", a.tmdbId).put("originalLanguage", a.originalLanguage)
        .put("popularity", a.popularity).put("rating", a.rating).put("voteCount", a.voteCount).put("firstAired", a.firstAired)
        .put("poster", a.poster).put("description", a.description).put("year", a.year).put("provider", a.provider).put("banner", a.banner)
        .put("status", a.status).put("endDate", a.endDate).put("overviewSource", a.overviewSource)
        .put("episodes", JSONArray().apply { a.episodes.forEach { put(JSONObject().put("id", it.id).put("title", it.title).put("season", it.seasonNumber).put("number", it.number).put("language", it.language)) } })
        .put("genres", JSONArray(a.genres)).put("aliases", JSONArray(a.aliases)).put("imdbId", a.imdbId).put("references", JSONArray().apply {
            a.references.forEach { put(JSONObject().put("provider", it.provider).put("id", it.id).put("tag", it.tag)) }
        }).put("localizedTitles", JSONObject().apply {
            // Build it explicitly: some Android API levels reject JSONObject(Map) when
            // the map comes from Kotlin's immutable Map implementation.
            a.localizedTitles.forEach { (key, value) -> put(key, value) }
        })
    private fun decodeAnime(o: JSONObject, loadSavedEpisodes: Boolean = true): Anime {
        val refs = o.optJSONArray("references")?.let { array -> (0 until array.length()).map { index ->
            val ref = array.getJSONObject(index)
            MediaReference(ref.optString("provider"), ref.optString("id"), ref.optString("tag"))
        } } ?: emptyList()
        val genres = o.optJSONArray("genres")?.let { array -> (0 until array.length()).map { array.getString(it) } } ?: emptyList()
        return Anime(title = o.getString("title"), tag = o.optString("tag"), isLive = o.optBoolean("isLive"), episodes = (o.optJSONArray("episodes")?.takeIf { it.length() > 0 } ?: if (!loadSavedEpisodes) null else runCatching {
                JSONArray(prefs.getString("episodes|${CatalogIdentity.title(o.optString("title"))}|${o.optInt("year").takeIf { it > 0 }}|${o.optString("tag")}", "[]"))
            }.getOrNull())?.let { array -> (0 until array.length()).map { i ->
                val e = array.getJSONObject(i)
                Episode(e.optString("title"), "", emptyList(), e.optString("id"), e.optInt("number", 1), e.optInt("season", 1), language = e.optString("language", "UNKNOWN"))
            } }.orEmpty(), id = o.optString("id"),
            poster = o.optString("poster"), description = o.optString("description"), year = o.optInt("year").takeIf { it > 0 },
            tmdbId = o.optInt("tmdbId").takeIf { it > 0 }, originalLanguage = o.optString("originalLanguage").takeIf { it.matches(Regex("[a-z]{2}")) },
            genres = genres, provider = o.optString("provider"), banner = o.optString("banner"), references = refs,
            aliases = o.optJSONArray("aliases")?.let { array -> (0 until array.length()).map { array.getString(it) } }.orEmpty(),
            localizedTitles = o.optJSONObject("localizedTitles")?.let { titles ->
                titles.keys().asSequence().associateWith { titles.optString(it) }.filterValues { it.isNotBlank() }
            }.orEmpty(),
            imdbId = o.optString("imdbId").takeIf { it.startsWith("tt") }, popularity = o.optDouble("popularity", 0.0), rating = o.optDouble("rating", 0.0), voteCount = o.optInt("voteCount", 0), firstAired = o.optString("firstAired").takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) },
            status = o.optString("status").takeIf { it.isNotBlank() }, endDate = o.optString("endDate").takeIf { it.isNotBlank() }, overviewSource = o.optString("overviewSource").takeIf { it.isNotBlank() })
    }
}
