package fr.nekotv

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*

/** Cache the cinema catalogue without resolving videos before the user chooses a film. */
class CinemaSpotlight(context: Context) {
    private val prefs = context.getSharedPreferences("cinema_spotlight", 0)
    private val gson = Gson()
    fun needsRefresh() = prefs.getInt("schema", 0) != 3 || System.currentTimeMillis() - prefs.getLong("checked", 0) > 6 * 60 * 60_000L
    fun cached(manager: StreamFlixProviderManager): List<Anime> {
        return runCatching { decodeCached(prefs.getString("films", "[]"), manager) }.getOrDefault(emptyList())
    }
    private fun decodeCached(raw: String?, manager: StreamFlixProviderManager): List<Anime> {
        val list = runCatching {
            gson.fromJson<List<Anime>>(raw, object : TypeToken<List<Anime>>() {}.type)
        }.getOrNull().orEmpty()
        return list.map { film -> film.copy(
            aliases = film.aliases.orEmpty(),
            references = film.references.orEmpty(),
            genres = film.genres.orEmpty(),
            episodes = film.episodes.orEmpty(),
            localizedTitles = film.localizedTitles ?: emptyMap()
        ) }.filter { film -> film.provider == "AlloCiné" || film.references.any { manager.isProviderEnabled(it.provider) } }
    }
    suspend fun refresh(manager: StreamFlixProviderManager, onPartial: (List<Anime>) -> Unit): List<Anime> {
        val films = coroutineScope {
            val releases = async { try { AlloCineReleases.load() } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() } }
            val catalogue = manager.cinemaCandidates()
            select(releases.await(), catalogue).filter { film -> film.provider == "AlloCiné" || film.references.any { manager.isProviderEnabled(it.provider) } }.take(12)
        }
        check(films.isNotEmpty()) { "Les services de sorties cinéma ne répondent pas. Réessayez." }
        prefs.edit().putInt("schema", 3).putString("films", gson.toJson(films)).putLong("checked", System.currentTimeMillis()).apply()
        onPartial(films)
        return films
    }
    companion object {
        internal fun select(releases: List<Anime>, catalogue: List<Anime>): List<Anime> {
            val matched = releases.map { release ->
                val names = (listOf(release.title) + release.aliases).map(CatalogIdentity::title).toSet()
                val found = catalogue.filter { item -> item.tag == "Film" && (item.year == null || item.year == release.year) &&
                    (listOf(item.title) + item.aliases).any { CatalogIdentity.title(it) in names } }
                if (found.isEmpty()) release.copy(provider = "AlloCiné", metadataUrl = release.id, references = emptyList(), episodes = emptyList()) else CatalogIdentity.merge(found).first().copy(
                    title = release.title, poster = release.poster.ifBlank { found.first().poster },
                    firstAired = release.firstAired, year = release.year, genres = (release.genres + found.flatMap { it.genres }).distinct(),
                    description = found.first().description.ifBlank { release.description })
            }
            val fallback = catalogue.filter { it.provider == "WarFlix" }.sortedByDescending { it.popularity }
            return CatalogIdentity.merge(fallback.take(6) + matched + fallback.drop(6)).map {
                if (it.provider == "AlloCiné") it.copy(references = emptyList(), episodes = emptyList()) else it
            }
        }
    }
}
