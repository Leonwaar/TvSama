package fr.nekotv

import android.app.Application
import android.content.Context
import com.streamflixreborn.streamflix.StreamFlixApp
import com.streamflixreborn.streamflix.models.*
import com.streamflixreborn.streamflix.providers.*
import com.streamflixreborn.streamflix.utils.*
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AnimeCoverageNetworkTest {
    @Test(timeout = 480_000) fun requestedAnimeExposeRealFrenchMedia() = runBlocking {
        assumeTrue(System.getProperty("tvsama.networkAudit") == "true")
        val app = StreamFlixApp()
        ReflectionHelpers.callInstanceMethod<Void>(app, "attach", ReflectionHelpers.ClassParameter.from(Context::class.java, RuntimeEnvironment.getApplication()))
        StreamFlixApp::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, app)
        UserPreferences.setup(app)
        val report = File("../verification/ANIME_COVERAGE_NETWORK.md")
        report.writeText("# Animés signalés — ${java.time.Instant.now()}\n\nAdaptateurs natifs, médias et segments contrôlés. Aucun décodage Android ni captcha humain simulé.\n\n")
        val missing = mutableListOf<String>()
        withContext(Dispatchers.IO) {
            for (provider in listOf(AnimeSamaProvider, VostfreeProvider)) for (title in listOf("Naruto", "Classroom of the Elite", "Mushoku Tensei")) {
                try { withTimeout(65_000) {
                    var cards = provider.search(title).filterIsInstance<TvShow>()
                    if (TitleAliases.variants(title).isNotEmpty()) {
                        val alias = TitleAliases.variants(title).firstOrNull { it != title && it.all { c -> c.code < 128 } && !it.startsWith("Moshoku") && it != "Classroom of Elite" }
                        if (alias != null) cards = cards + provider.search(alias).filterIsInstance<TvShow>()
                    }
                    val exact = cards.filter { CatalogIdentity.title(it.title) == CatalogIdentity.title(title) }
                    check(exact.isNotEmpty()) { "Titre exact absent" }
                    var usable = 0
                    for (card in exact.distinctBy { it.id }.take(4)) {
                        val show = provider.getTvShow(card.id)
                        val season = show.seasons.firstOrNull { it.number == 1 }
                        if (season == null) { report.appendText("  Fiche ${card.title} : saisons ${show.seasons.map { it.number }} ; contrôle S1 sur une autre fiche.\n"); continue }
                        val episodes = season.episodes.ifEmpty { provider.getEpisodesBySeason(season.id) }
                        val first = episodes.filter { it.number == 1 }
                        check(first.isNotEmpty()) { "Épisode 1 absent" }
                        report.appendText("- ${provider.name} · $title : ${episodes.size} épisodes/versions ; langues ${first.map { it.title }.joinToString()}.\n")
                        for (ep in first) {
                            val type = Video.Type.Episode(ep.id, ep.number, ep.title.orEmpty(), ep.poster.orEmpty(), ep.overview.orEmpty(),
                                Video.Type.Episode.TvShow(show.id, show.title, show.poster.orEmpty(), show.banner.orEmpty(), null, show.imdbId),
                                Video.Type.Episode.Season(season.number, "Saison 1"))
                            val servers = provider.getServers(ep.id, type)
                            report.appendText("  Lecteurs publiés : ${servers.size}.\n")
                            for (server in servers.sortedBy { if (it.name.contains("ansembed")) 0 else 1 }.take(4)) {
                                try {
                                    val media = withTimeout(14_000) { withContext(ExtractionProvider(provider)) {
                                        val video = provider.getVideo(server)
                                        validateLiveSource(VideoSource(server.name, video.source, "UNKNOWN", "Auto", provider.name, video.headers.orEmpty()), minimumVodDurationMs = 90_000)
                                    } }
                                    if (media != null) {
                                        usable++
                                        report.appendText("  ${server.name} : média et segment accessibles.\n")
                                    } else report.appendText("  ${server.name} : média non confirmé.\n")
                                } catch (e: TimeoutCancellationException) { currentCoroutineContext().ensureActive(); report.appendText("  ${server.name} : délai dépassé.\n") }
                                catch (e: CancellationException) { throw e }
                                catch (e: Exception) { report.appendText("  ${server.name} : ${e.javaClass.simpleName}.\n") }
                            }
                        }
                    }
                    check(usable > 0) { "Aucun média confirmé" }
                } } catch (e: TimeoutCancellationException) { currentCoroutineContext().ensureActive(); missing += "${provider.name}/$title"; report.appendText("  Budget total dépassé.\n") }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { missing += "${provider.name}/$title"; report.appendText("  Échec : ${e.javaClass.simpleName} · ${e.message?.replace(Regex("https?://[^ ]+"), "[URL]")?.take(100)}\n") }
            }
        }
        assertTrue("Médias non confirmés : $missing", missing.isEmpty())
    }
}
