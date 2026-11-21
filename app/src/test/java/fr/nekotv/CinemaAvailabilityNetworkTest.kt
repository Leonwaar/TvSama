package fr.nekotv

import android.app.Application
import com.streamflixreborn.streamflix.StreamFlixApp
import com.streamflixreborn.streamflix.providers.WiflixProvider
import com.streamflixreborn.streamflix.utils.UserPreferences
import kotlinx.coroutines.*
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.PAUSED)
class CinemaAvailabilityNetworkTest {
    @Test fun auditEachDisplayedReleaseWithoutConfusingCatalogueWithPlayback() {
        assumeTrue(System.getProperty("tvsama.networkAudit") == "true")
        val task = CoroutineScope(Dispatchers.Default).async { audit() }
        val deadline = System.currentTimeMillis() + 420_000
        try {
            while (!task.isCompleted && System.currentTimeMillis() < deadline) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                Thread.sleep(50)
            }
            check(task.isCompleted) { "Le contrôle réseau a dépassé son budget total" }
            runBlocking { task.await() }
        } finally { task.cancel() }
    }
    private suspend fun audit() {
        val context = RuntimeEnvironment.getApplication()
        val application = StreamFlixApp()
        org.robolectric.util.ReflectionHelpers.callInstanceMethod<Void>(application, "attach",
            org.robolectric.util.ReflectionHelpers.ClassParameter.from(android.content.Context::class.java, context))
        StreamFlixApp::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, application)
        UserPreferences.setup(context)
        UserPreferences.setProviderCache(WiflixProvider, UserPreferences.PROVIDER_AUTOUPDATE, "false")
        val manager = StreamFlixProviderManager.getInstance()
        val films = CinemaSpotlight.select(AlloCineReleases.load(), manager.cinemaCandidates()).take(12)
        assertTrue("Aucune sortie à contrôler", films.isNotEmpty())
        val report = File("../verification/CINEMA_PLAYBACK.md")
        report.parentFile?.mkdirs()
        report.writeText("# Disponibilité des sorties cinéma — ${java.time.Instant.now()}\n\nContrôle HTTP du média et de son type, pas du décodage Android. Une protection interactive ne prouve pas l’absence du film.\n\n| Film | Catalogue trouvé | Contrôle du flux |\n|---|---|---|\n")
        for (film in films) {
            var playable: Anime? = null
            var partial = emptyList<VideoSource>()
            val started = System.currentTimeMillis()
            try {
                val resolved = withTimeoutOrNull(22_000) {
                    playable = if (film.provider == "AlloCiné") manager.findCinemaMovie(film) else film
                    manager.resolveSources(playable!!, language = "VF", onPartial = { partial = it })
                } ?: partial
                val providers = playable?.references.orEmpty().map { it.provider }.distinct().joinToString().ifBlank { "Non confirmé" }
                val french = resolved.filter { it.reachable && it.language in setOf("VF", "VOSTFR") }
                val status = when {
                    french.isNotEmpty() -> "Flux VF/VOSTFR accessible : " + french.map { it.provider }.distinct().joinToString()
                    resolved.isNotEmpty() -> "Flux accessible, langue non confirmée"
                    else -> "Aucun flux confirmé ; validation web potentiellement nécessaire"
                }
                report.appendText("| ${film.title.replace('|', '/')} | $providers | $status |\n")
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                report.appendText("| ${film.title.replace('|', '/')} | Recherche sans résultat confirmé | Aucun flux confirmé |\n")
            }
            report.appendText("<!-- Contrôle terminé en ${System.currentTimeMillis() - started} ms -->\n")
        }
    }
}
