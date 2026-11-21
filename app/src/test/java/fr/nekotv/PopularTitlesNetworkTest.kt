package fr.nekotv

import android.app.Application
import android.content.Context
import com.streamflixreborn.streamflix.StreamFlixApp
import com.streamflixreborn.streamflix.utils.UserPreferences
import kotlinx.coroutines.*
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PopularTitlesNetworkTest {
    private fun prepare(): StreamFlixProviderManager {
        assumeTrue(System.getProperty("tvsama.networkAudit") == "true")
        val app = StreamFlixApp()
        ReflectionHelpers.callInstanceMethod<Void>(app, "attach",
            ReflectionHelpers.ClassParameter.from(Context::class.java, RuntimeEnvironment.getApplication()))
        StreamFlixApp::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, app)
        UserPreferences.setup(RuntimeEnvironment.getApplication())
        return StreamFlixProviderManager.getInstance()
    }
    private suspend fun <T> stage(name: String, block: suspend () -> T): T = coroutineScope {
            println("Audit : $name")
            val task = async(Dispatchers.IO) { withTimeout(90_000) { block() } }
            while (!task.isCompleted) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                delay(10)
            }
            task.await()
    }
    @Test(timeout = 360_000) fun narutoAndKnownFilmHaveVerifiedFrenchMedia() = runBlocking {
        val manager = prepare()
        for ((title, category) in listOf("Naruto" to "Séries", "Fight Club" to "Films")) {
            val card = stage("recherche $title") { manager.searchAllProviders(title, category) }.firstOrNull {
                CatalogIdentity.title(it.title) == CatalogIdentity.title(title)
            }
            assertTrue("$title absent du catalogue", card != null)
            val details = stage("fiche $title") { manager.loadDetails(card!!) }
            println("$title : premiers épisodes ${details.episodes.take(8).map { "S${it.seasonNumber}E${it.number}:${it.id.take(65)}" }}")
            val episode = details.firstEpisode()
            assertTrue("$title : pas d’épisode/film", episode != null)
            if (title == "Fight Club") stage("préparation discrète $title") { manager.prefetchServers(details, episode) }
            val vf = stage("VF $title") { manager.resolveSources(details, episode, "VF") }
            val vostfr = stage("VOSTFR $title") { manager.resolveSources(details, episode, "VOSTFR") }
            println("$title : références ${details.references.map { it.provider }}, VF ${vf.map { it.provider + "/" + it.name }}, VOSTFR ${vostfr.map { it.provider + "/" + it.name }}")
            assertTrue("$title : aucun média français vérifié", (vf + vostfr).any { it.reachable })
        }
    }

    @Test(timeout = 120_000) fun accessibleBytesDoNotClaimAFullPlayback() = runBlocking {
        val manager = prepare()
        val verified = stage("vérification ciblée FrenchStreaming") { manager.verifySourceMedia("FrenchStreaming") }
        assertTrue("FrenchStreaming : aucun échantillon média accessible", verified)
        assertTrue(manager.sourceHealth()["FrenchStreaming"].orEmpty().startsWith("○"))
    }

    @Test(timeout = 180_000) fun wiflixKnownMovieHasVerifiedMedia() = runBlocking {
        prepare()
        val provider = com.streamflixreborn.streamflix.providers.WiflixProvider
        val film = stage("recherche Wiflix Fight Club") {
            provider.search("Fight Club").filterIsInstance<com.streamflixreborn.streamflix.models.Movie>()
                .first { CatalogIdentity.title(it.title) == CatalogIdentity.title("Fight Club") }
        }
        val details = stage("fiche Wiflix Fight Club") { provider.getMovie(film.id) }
        UserPreferences.currentProvider = provider
        val servers = stage("lecteurs Wiflix Fight Club") { provider.getServers(film.id,
            com.streamflixreborn.streamflix.models.Video.Type.Movie(film.id, details.title, "1999", details.poster.orEmpty(), details.imdbId)) }
        var verified = false
        for (server in servers) {
            try {
                val source = stage("média Wiflix ${server.name}") { withTimeout(8_000) {
                    val video = provider.getVideo(server)
                    validateLiveSource(VideoSource(server.name, video.source, mediaLanguage(server.name, emptyList()), "Auto", provider.name, video.headers.orEmpty())) {
                        println("Wiflix ${server.name} : $it")
                    }
                } }
                if (source != null) { println("Wiflix Fight Club : ${server.name}, média vérifié"); verified = true; break }
            } catch (e: CancellationException) { if (e !is TimeoutCancellationException) throw e }
            catch (e: Exception) { println("Wiflix ${server.name} : ${e.message.orEmpty().replace(Regex("https?://[^ ]+"), "[URL]").take(160)}") }
        }
        assertTrue("Wiflix Fight Club : aucun média vérifié parmi ${servers.map { it.name }}", verified)
    }
}
