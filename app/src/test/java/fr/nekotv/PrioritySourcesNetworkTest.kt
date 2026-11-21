package fr.nekotv

import android.app.Application
import android.content.Context
import com.streamflixreborn.streamflix.StreamFlixApp
import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.providers.*
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
import java.io.File

/** An HTTP 200 or a usable catalogue cannot satisfy this release gate. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PrioritySourcesNetworkTest {
    @Test(timeout = 120_000) fun wiflixRecoversItsCatalogueWithoutTheDirectory() = runBlocking {
        assumeTrue(System.getProperty("tvsama.networkAudit") == "true")
        val app = StreamFlixApp()
        ReflectionHelpers.callInstanceMethod<Void>(app, "attach", ReflectionHelpers.ClassParameter.from(Context::class.java, RuntimeEnvironment.getApplication()))
        StreamFlixApp::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, app)
        UserPreferences.setup(app)
        val report = File("../verification/WIFLIX_RECOVERY_NETWORK.md")
        report.writeText("# Wiflix : récupération autonome — ${java.time.Instant.now()}\n\nSans annuaire OùStreamer. Le catalogue et les lecteurs publiés ne prouvent pas le décodage Android.\n\n")
        try {
            withContext(Dispatchers.IO) { withTimeout(90_000) {
                val address = WiflixProvider.onChangeUrl(forceRefresh = true)
                val cards = WiflixProvider.getHome().flatMap { it.list }
                check(cards.isNotEmpty()) { "Catalogue vide" }
                check(com.streamflixreborn.streamflix.utils.SourceAddresses.current("Wiflix", "") == address) { "Adresse non mémorisée" }
                report.appendText("${cards.size} fiches ; adresse du catalogue validée et mémorisée.\n")
                val movie = WiflixProvider.getMovie("186-clueless.html")
                check(movie.title.contains("Clueless", true)) { "Autre programme retourné" }
                val servers = WiflixProvider.getServers(movie.id, Video.Type.Movie(movie.id, movie.title, "1995", movie.poster.orEmpty(), movie.imdbId))
                report.appendText("Clueless : ${servers.size} lecteurs publiés.\n")
                var usable = 0
                for (server in servers.take(10)) {
                    try {
                        val media = withTimeout(12_000) {
                            withContext(com.streamflixreborn.streamflix.utils.ExtractionProvider(WiflixProvider)) {
                                val video = WiflixProvider.getVideo(server)
                                validateLiveSource(VideoSource(server.name, video.source, "UNKNOWN", "Auto", "Wiflix", video.headers.orEmpty()), minimumVodDurationMs = 90_000)
                            }
                        }
                        if (media != null) { usable++; report.appendText("- ${server.name} : signature média et segment accessibles ; aucun HLS court accepté.\n") }
                    } catch (e: TimeoutCancellationException) { currentCoroutineContext().ensureActive() }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { }
                }
                report.appendText("$usable lecteurs accessibles sur les ${minOf(10, servers.size)} premiers testés. Lecture Android non confirmée.\n")
                check(usable > 0) { "Aucun média accessible confirmé" }
            } }
        } catch (e: Exception) {
            report.appendText("Échec : ${e.javaClass.simpleName}. Aucun catalogue fonctionnel confirmé.\n")
            throw e
        }
    }
    @Test(timeout = 300_000) fun allThreePrioritySourcesExposeAccessibleMedia() = runBlocking {
        assumeTrue(System.getProperty("tvsama.networkAudit") == "true")
        val app = StreamFlixApp()
        ReflectionHelpers.callInstanceMethod<Void>(app, "attach", ReflectionHelpers.ClassParameter.from(Context::class.java, RuntimeEnvironment.getApplication()))
        StreamFlixApp::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, app)
        UserPreferences.setup(RuntimeEnvironment.getApplication())
        val manager = StreamFlixProviderManager.getInstance()
        val report = File("../verification/PRIORITY_SOURCES.md")
        report.writeText("# Trois sources prioritaires — ${java.time.Instant.now()}\n\nContrôle des adaptateurs et des octets média ; décodage Android et captchas interactifs non simulés.\n\n")
        withContext(Dispatchers.IO) { manager.refreshDirectory() }
        val failures = mutableListOf<String>()
        supervisorScope {
        for (provider in listOf(WiflixProvider, PapaduStreamProvider, FrAnimeProvider)) {
            assertTrue("Adresse non mémorisée pour ${provider.name}",
                com.streamflixreborn.streamflix.utils.SourceAddresses.current(provider.name, "") == provider.baseUrl)
            report.appendText("\n${provider.name} · adresse locale mémorisée : ${provider.baseUrl}\n")
            val task = async(Dispatchers.IO) {
                withTimeout(80_000) {
                    check(provider.getHome().any { it.list.isNotEmpty() }) { "Catalogue vide" }
                    val (id, type) = if (provider == WiflixProvider) {
                        val movie = provider.getMovie("186-clueless.html")
                        check(movie.title.contains("Clueless", true)) { "Redirection vers un autre titre" }
                        movie.id to Video.Type.Movie(movie.id, movie.title, "1995", movie.poster.orEmpty(), movie.imdbId)
                    } else {
                        val id = if (provider == FrAnimeProvider) "${provider.baseUrl}anime/11" else
                            "${provider.baseUrl}cat-series/series-vf/12088-indefendable.html"
                        val show = provider.getTvShow(id)
                        val season = show.seasons.first { it.number == 1 }
                        val episode = provider.getEpisodesBySeason(season.id).first { it.number == 1 }
                        episode.id to Video.Type.Episode(episode.id, episode.number, episode.title.orEmpty(), episode.poster.orEmpty(), episode.overview.orEmpty(),
                            Video.Type.Episode.TvShow(show.id, show.title, show.poster.orEmpty(), show.banner.orEmpty(), null, show.imdbId),
                            Video.Type.Episode.Season(season.number, "Saison 1"))
                    }
                    val servers = provider.getServers(id, type)
                    check(servers.isNotEmpty()) { "Aucun lecteur publié" }
                    UserPreferences.currentProvider = provider
                    var lastFailure = "Aucun média accessible"
                    for (server in servers.take(8)) {
                        try {
                            val media = withTimeout(8_000) {
                                val video = provider.getVideo(server)
                                validateLiveSource(VideoSource(server.name, video.source, "UNKNOWN", "Auto", provider.name, video.headers.orEmpty())) { lastFailure = it }
                            }
                            if (media != null) return@withTimeout "${server.name} · manifeste/conteneur et segment accessibles"
                        } catch (e: TimeoutCancellationException) { currentCoroutineContext().ensureActive(); lastFailure = "Extraction trop lente" }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) { lastFailure = e.message.orEmpty() }
                    }
                    error(lastFailure)
                }
            }
            while (!task.isCompleted) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                delay(10)
            }
            try {
                report.appendText("- ${provider.name} : ${task.await()}\n")
            } catch (e: Exception) {
                val message = e.message.orEmpty().replace(Regex("https?://[^ ]+"), "[URL]").replace('\n', ' ').take(200)
                failures += provider.name
                report.appendText("- ${provider.name} : ÉCHEC · $message\n")
            }
        }
        }
        assertTrue("Release bloquée : ${failures.joinToString()}", failures.isEmpty())
    }
}
