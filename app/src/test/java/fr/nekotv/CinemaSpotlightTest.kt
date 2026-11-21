package fr.nekotv

import android.app.Application
import com.google.gson.Gson
import com.streamflixreborn.streamflix.StreamFlixApp
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class CinemaSpotlightTest {
    private val context get() = RuntimeEnvironment.getApplication()
    @Before fun prepare() {
        val application = StreamFlixApp()
        org.robolectric.util.ReflectionHelpers.callInstanceMethod<Void>(application, "attach",
            org.robolectric.util.ReflectionHelpers.ClassParameter.from(android.content.Context::class.java, context))
        StreamFlixApp::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, application)
        context.getSharedPreferences("tvsama_settings", 0).edit().clear().commit()
        context.getSharedPreferences("cinema_spotlight", 0).edit().clear().commit()
    }
    @Test fun cinemaMetadataOnlyBecomesPlayableWithMatchingCatalogueReferences() {
        val metadata = Anime("Un film", "Film", emptyList(), "https://allocine.fr/film/42", year = 2026, firstAired = "2026-10-14")
        val source = metadata.copy(id = "https://source.example/film/42", provider = "Coflix", references = listOf(MediaReference("Coflix", "https://source.example/film/42", "Film")))
        val unlinked = CinemaSpotlight.select(listOf(metadata), emptyList()).single()
        assertEquals("AlloCiné", unlinked.provider)
        assertEquals(metadata.title, unlinked.title)
        assertTrue(unlinked.references.isEmpty())
        assertTrue(unlinked.episodes.isEmpty())
        val selected = CinemaSpotlight.select(listOf(metadata), listOf(source)).single()
        assertEquals(source.id, selected.id)
        assertEquals(source.references, selected.references)
        assertEquals("2026-10-14", selected.firstAired)
        assertTrue(CinemaSpotlight.select(listOf(metadata), listOf(source.copy(year = 1990))).single().references.isEmpty())
    }
    @Test fun metadataStaysVisibleWhenPlaybackProvidersAreDisabled() {
        val manager = StreamFlixProviderManager.getInstance()
        val release = Anime("Sortie cinéma", "Film", emptyList(), "https://www.allocine.fr/film/42", provider = "AlloCiné")
        val prefs = context.getSharedPreferences("cinema_spotlight", 0)
        prefs.edit().putString("films", Gson().toJson(listOf(release))).commit()
        manager.setProviderEnabled("WarFlix", false)
        assertEquals(listOf(release), CinemaSpotlight(context).cached(manager))
        manager.setProviderEnabled("WarFlix", true)
    }
    @Test fun allocineParserKeepsCurrentReleasesAndRejectsFutureAndReissues() {
        val doc = org.jsoup.Jsoup.parse("""<div class="entity-card"><a class="meta-title-link" href="/film/42">Film actuel</a><div class="meta-body-info"><span class="date">14 octobre 2026</span><span class="dark-grey-link">Action</span></div><img class="thumbnail-img" data-src="https://img.example/42.jpg"><div class="synopsis"><div class="content-txt">Une aventure.</div></div></div>""", "https://www.allocine.fr/")
        val film = AlloCineReleases.parse(doc, java.time.LocalDate.of(2026, 10, 15)).single()
        assertEquals("2026-10-14", film.firstAired)
        assertEquals("https://img.example/42.jpg", film.poster)
        assertTrue(film.references.isEmpty())
        assertTrue(AlloCineReleases.parse(doc, java.time.LocalDate.of(2026, 10, 13)).isEmpty())
        assertTrue(AlloCineReleases.parse(doc, java.time.LocalDate.of(2027, 10, 15)).isEmpty())
    }
    @Test fun cachedCinemaKeepsFallbackUntilRefreshAndHonorsEnabledSources() {
        val manager = StreamFlixProviderManager.getInstance()
        val film = Anime("Film disponible", "Film", emptyList(), "film", provider = "Test",
            references = listOf(MediaReference("Test", "film", "Film")))
        val prefs = context.getSharedPreferences("cinema_spotlight", 0)
        prefs.edit().putString("films", Gson().toJson(listOf(film))).putLong("checked", System.currentTimeMillis()).commit()
        val cinema = CinemaSpotlight(context)
        assertEquals(listOf(film), cinema.cached(manager))
        manager.setProviderEnabled("Test", false)
        assertTrue(cinema.cached(manager).isEmpty())
        manager.setProviderEnabled("Test", true)
        prefs.edit().putLong("checked", System.currentTimeMillis() - 7 * 60 * 60_000L).commit()
        assertEquals(listOf(film), cinema.cached(manager))
        assertTrue(cinema.needsRefresh())
        prefs.edit().putString("films", "invalid JSON").putLong("checked", System.currentTimeMillis()).commit()
        assertTrue(cinema.cached(manager).isEmpty())
    }
}
