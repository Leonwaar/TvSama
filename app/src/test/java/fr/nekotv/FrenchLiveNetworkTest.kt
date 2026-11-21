package fr.nekotv

import android.app.Application
import android.content.Context
import com.streamflixreborn.streamflix.StreamFlixApp
import com.streamflixreborn.streamflix.utils.UserPreferences
import kotlinx.coroutines.*
import org.junit.Assert.*
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
class FrenchLiveNetworkTest {
    @Test(timeout = 300_000) fun frenchChannelsExposeNativeMediaAndMirrorsArePublished() = runBlocking {
        assumeTrue(System.getProperty("tvsama.networkAudit") == "true")
        val app = StreamFlixApp()
        ReflectionHelpers.callInstanceMethod<Void>(app, "attach", ReflectionHelpers.ClassParameter.from(Context::class.java, RuntimeEnvironment.getApplication()))
        StreamFlixApp::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, app)
        UserPreferences.setup(app)

        val report = File("../verification/FRENCH_LIVE_NETWORK.md")
        report.writeText("# Direct français — ${java.time.Instant.now()}\n\nContrôle des flux et segments par le client natif, sans décodage Android.\n\n")
        val channels = FrenchLiveTv.events(force = true)
        report.appendText("${channels.size} chaînes françaises avec au moins une adresse HLS publiée.\n")
        assertTrue(channels.isNotEmpty())
        var confirmed = 0
        for (name in listOf("Arte", "BFM TV", "L’Équipe", "France TV Sport", "Sport en France", "Equidia")) {
            val event = channels.firstOrNull { it.title.replace("’", "'").equals(name.replace("’", "'"), true) }
                
            if (event == null) { report.appendText("- $name : absent du catalogue public.\n"); continue }
            val readers = FrenchLiveTv.servers(event.url)
            report.appendText("- $name : ${readers.size} adresse(s) publiée(s).\n")
            for (reader in readers) {
                val checked = validateLiveSource(VideoSource(reader.name, reader.src, "VF", "Auto", "TVradioZap"))
                report.appendText("  ${java.net.URI(reader.src).host} : ${if (checked != null) "média et segment accessibles" else "média non confirmé"}.\n")
                if (checked != null) confirmed++
            }
        }
        val mirrors = SourceDirectory.liveWiki()
        report.appendText("\nStreamed : ${mirrors.size} adresses découvertes dans FMHY ; ceci ne valide pas leurs vidéos.\n")
        assertTrue(mirrors.size >= 2)
        assertTrue("Aucun direct français natif confirmé", confirmed > 0)
    }
}
