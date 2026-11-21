package fr.nekotv

import android.app.Application
import android.content.Context
import com.streamflixreborn.streamflix.StreamFlixApp
import com.streamflixreborn.streamflix.models.Movie
import com.streamflixreborn.streamflix.providers.Provider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class RelatedCatalogueDiscoveryTest {
    @Test fun aliasesFindTheSameProgrammeWithoutMergingAnotherRemakeOrImdbIdentity() {
        val movie = Anime("Titre français", "Film", emptyList(), "wanted", year = 2026, aliases = listOf("English title"))
        val translated = movie.copy(title = "English title", id = "match", aliases = emptyList())
        assertEquals(listOf(translated), relatedCatalogueMatches(movie, listOf(translated, translated.copy(id = "old", year = 1990))))
        assertTrue(relatedCatalogueMatches(movie.copy(imdbId = "tt1234567"), listOf(translated.copy(imdbId = "tt7654321"))).isEmpty())
        assertTrue(relatedCatalogueMatches(movie.copy(year = null), listOf(translated, translated.copy(id = "old", year = 1990))).isEmpty())
    }
    @Test fun twoKnownSitesDoNotPreventDiscoveryOfAnAliasOnAThirdSite() = runBlocking {
        val app = StreamFlixApp()
        ReflectionHelpers.callInstanceMethod<Void>(app, "attach",
            ReflectionHelpers.ClassParameter.from(Context::class.java, RuntimeEnvironment.getApplication()))
        StreamFlixApp::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, app)
        com.streamflixreborn.streamflix.utils.UserPreferences.setup(app)
        val searches = AtomicInteger()
        val providers = (1..4).map { index ->
            java.lang.reflect.Proxy.newProxyInstance(Provider::class.java.classLoader, arrayOf(Provider::class.java)) { proxy, method, args ->
                fun movie() = Movie("movie-$index", if (index > 2) "The Programme" else "Le programme",
                    released = if (index == 4) "1990-01-01" else "2026-01-01")
                when (method.name) {
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.get(0)
                    "getName" -> "Discovery$index"
                    "getBaseUrl" -> "https://discovery$index.example/"
                    "getLanguage" -> "fr"
                    "getMovie" -> movie()
                    "getHome" -> throw kotlinx.coroutines.CancellationException("Test interrupted")
                    "search" -> {
                        searches.incrementAndGet()
                        if (index == 1 && args!![0] == "The Simpsons") listOf(movie().apply { title = "Les Simpson" })
                        else if (index == 1 && args!![0] == "Les Simpson") throw java.io.IOException("Alias temporarily unavailable")
                        else
                        if (index <= 2 || (args!![0] as String).equals("The Programme", true)) listOf(movie()) else emptyList<Movie>()
                    }
                    else -> null
                }
            } as Provider
        }
        val manager = StreamFlixProviderManager.getInstance()
        val field = manager.javaClass.getDeclaredField("providers\$delegate").apply { isAccessible = true }
        val original = field.get(manager)
        try {
            field.set(manager, lazy { providers })
            val anime = Anime("Le programme", "Film", emptyList(), "movie-1", year = 2026, provider = "Discovery1",
                aliases = listOf("The Programme"), references = listOf(
                    MediaReference("Discovery1", "movie-1", "Film"), MediaReference("Discovery2", "movie-2", "Film")))
            val found = manager.loadDetails(anime)
            assertEquals(setOf("Discovery1", "Discovery2", "Discovery3"), found.references.map { it.provider }.toSet())
            val searched = searches.get()
            assertTrue(searched > 0)
            manager.loadDetails(anime)
            assertEquals("Repeated opening must reuse the title lookup", searched, searches.get())
            val partial = manager.searchAllProviders("The Simpsons", "Films", includePopularity = false)
            assertTrue("A later alias failure must preserve an already published result", partial.any {
                it.references.any { ref -> ref.provider == "Discovery1" }
            })
            try {
                manager.verifySourceMedia("Discovery1")
                fail("Cancellation must propagate")
            } catch (_: kotlinx.coroutines.CancellationException) {
                assertTrue(manager.sourceHealth()["Discovery1"].orEmpty().contains("Test interrompu"))
            }
        } finally { field.set(manager, original) }
    }
}
