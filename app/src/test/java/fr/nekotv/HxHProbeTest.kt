package fr.nekotv

import android.app.Application
import com.streamflixreborn.streamflix.StreamFlixApp
import com.streamflixreborn.streamflix.utils.UserPreferences
import kotlinx.coroutines.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class HxHProbeTest {
    @Test(timeout = 240_000) fun probeHunterHunterVostfr() = runBlocking {
        assumeTrue(System.getProperty("tvsama.networkAudit") == "true")
        val context = RuntimeEnvironment.getApplication()
        val application = StreamFlixApp()
        org.robolectric.util.ReflectionHelpers.callInstanceMethod<Void>(application, "attach",
            org.robolectric.util.ReflectionHelpers.ClassParameter.from(android.content.Context::class.java, context))
        StreamFlixApp::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, application)
        UserPreferences.setup(context)
        val manager = StreamFlixProviderManager.getInstance()
        runCatching { withTimeout(30_000) { manager.refreshDirectory() } }
        val report = File("../verification/HXH_PROBE.md")
        report.parentFile?.mkdirs()
        report.writeText("# Hunter x Hunter probe — ${java.time.Instant.now()}\n\n")

        val searchStart = System.currentTimeMillis()
        val cards = withTimeout(60_000) { manager.searchAllProviders("Hunter x Hunter", "Séries") }
        val searchMs = System.currentTimeMillis() - searchStart
        report.appendText("## Search (${searchMs} ms, ${cards.size} cards)\n\n")
        cards.forEach { card ->
            report.appendText("- ${card.title} | ${card.tag} | year=${card.year} | tmdb=${card.tmdbId} | imdb=${card.imdbId} | refs=${card.references.joinToString { "${it.provider}:${it.tag}:${it.id}" }}\n")
        }

        val target = cards.firstOrNull { card ->
            (listOf(card.title) + card.aliases).any { CatalogIdentity.title(it) == CatalogIdentity.title("Hunter x Hunter") }
        } ?: cards.firstOrNull()
        report.appendText("\nTarget: ${target?.title} (${target?.tag}, ${target?.provider})\n")
        if (target == null) { println(report.readText()); return@runBlocking }

        val loaded = withTimeout(90_000) { manager.loadDetails(target) }
        report.appendText("Loaded: ${loaded.title} episodes=${loaded.episodes.size} refs=${loaded.references.joinToString { "${it.provider}:${it.tag}:${it.id}" }}\n")
        val episode = loaded.episodes.firstOrNull { it.seasonNumber == 1 && it.number == 1 } ?: loaded.episodes.firstOrNull()
        report.appendText("Episode: ${episode?.id} S${episode?.seasonNumber}E${episode?.number}\n\n")

        val resolveStart = System.currentTimeMillis()
        val sources = withTimeout(120_000) { manager.resolveSources(loaded, episode, "VOSTFR") }
        val resolveMs = System.currentTimeMillis() - resolveStart
        report.appendText("## VOSTFR sources (${resolveMs} ms, ${sources.size})\n\n")
        sources.forEach { source ->
            report.appendText("- ${source.provider} | ${source.name} | ${source.language} | ${source.mimeType} | subs=${source.subtitles.size} | reachable=${source.reachable} | ${source.url.take(120)}\n")
        }
        println(report.readText())
    }
}
