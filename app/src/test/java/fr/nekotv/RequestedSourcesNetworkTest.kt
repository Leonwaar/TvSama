package fr.nekotv

import android.app.Application
import com.streamflixreborn.streamflix.StreamFlixApp
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.providers.*
import com.streamflixreborn.streamflix.utils.UserPreferences
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class RequestedSourcesNetworkTest {
    @Test fun requestedTitlesHaveIndependentCatalogueReferences() = runBlocking {
        assumeTrue(System.getProperty("tvsama.networkAudit") == "true")
        val context = RuntimeEnvironment.getApplication()
        val application = StreamFlixApp()
        org.robolectric.util.ReflectionHelpers.callInstanceMethod<Void>(application, "attach",
            org.robolectric.util.ReflectionHelpers.ClassParameter.from(android.content.Context::class.java, context))
        StreamFlixApp::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, application)
        UserPreferences.setup(context)
        val manager = StreamFlixProviderManager.getInstance()
        val report = File("../verification/CATALOGUE_DIVERSITY_NETWORK.md")
        report.parentFile?.mkdirs()
        report.writeText("# Diversité des catalogues — ${java.time.Instant.now()}\n\nRéférences de catalogue uniquement ; aucune preuve de lecture.\n\n")
        val missing = mutableListOf<String>()
        for ((query, category) in listOf("Resident Evil" to "Films", "La La Land" to "Films", "Simpson" to "Séries")) {
            val cards = manager.searchAllProviders(query, category)
            val matched = cards.filter { CatalogIdentity.title(it.title).contains(CatalogIdentity.title(query)) }
            val providers = matched.flatMap { card -> card.references.map { it.provider } }.distinct()
            println("$query : $providers")
            report.appendText("- $query : ${providers.size} catalogues distincts (${providers.joinToString()}).\n")
            if (providers.size < 2) missing += query
        }
        assertTrue("Moins de deux catalogues indépendants : $missing", missing.isEmpty())
    }
    @Test fun recoveredAnimeSamaAndKidrazCataloguesAreSearchable() = runBlocking {
        assumeTrue(System.getProperty("tvsama.networkAudit") == "true")
        val context = RuntimeEnvironment.getApplication()
        val application = StreamFlixApp()
        org.robolectric.util.ReflectionHelpers.callInstanceMethod<Void>(application, "attach",
            org.robolectric.util.ReflectionHelpers.ClassParameter.from(android.content.Context::class.java, context))
        StreamFlixApp::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, application)
        UserPreferences.setup(context)
        val manager = StreamFlixProviderManager.getInstance()
        manager.refreshDirectory()
        assertTrue("Anime-Sama : catalogue vide", AnimeSamaProvider.getTvShows(1).isNotEmpty())
        assertTrue("Anime-Sama : Naruto introuvable", AnimeSamaProvider.search("Naruto").isNotEmpty())
        val films = KidrazProvider.getMovies(1)
        assertTrue("Kidraz : catalogue vide après récupération de l’adresse", films.isNotEmpty())
        assertTrue("Kidraz : recherche absente", KidrazProvider.search(films.first().title.split(' ').first()).isNotEmpty())
    }
    @Test fun cinemaUsesRealFrenchReleasesAndNativeCatalogueReferences() = runBlocking {
        assumeTrue(System.getProperty("tvsama.networkAudit") == "true")
        val context = RuntimeEnvironment.getApplication()
        val application = StreamFlixApp()
        org.robolectric.util.ReflectionHelpers.callInstanceMethod<Void>(application, "attach",
            org.robolectric.util.ReflectionHelpers.ClassParameter.from(android.content.Context::class.java, context))
        StreamFlixApp::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, application)
        UserPreferences.setup(context)
        UserPreferences.setProviderCache(WiflixProvider, UserPreferences.PROVIDER_AUTOUPDATE, "false")
        val oldWarflixPage = "https://warflix.im/film/1423191/"
        com.streamflixreborn.streamflix.utils.SourceAddresses.update("WarFlix", oldWarflixPage, oldWarflixPage)
        val releases = AlloCineReleases.load()
        assertTrue("AlloCiné : sorties françaises absentes", releases.isNotEmpty())
        val manager = StreamFlixProviderManager.getInstance()
        val catalogue = manager.cinemaCandidates()
        assertEquals("https://warflix.im/", WarFlixProvider.baseUrl)
        assertTrue("WarFlix : API inaccessible après migration de l’ancienne URL", catalogue.any { it.provider == "WarFlix" })
        val selected = CinemaSpotlight.select(releases, catalogue)
        assertTrue("Cinéma : aucune référence de lecture", selected.isNotEmpty())
        assertTrue(selected.all { film -> film.provider == "AlloCiné" && film.references.isEmpty() || film.references.isNotEmpty() && film.references.all { it.provider != "AlloCiné" } })
        val report = File("../verification/CINEMA_AND_LIVE.md")
        report.parentFile?.mkdirs()
        report.writeText("Contrôle réseau ${java.time.Instant.now()}\n\nAlloCiné : ${releases.size} sorties françaises.\nCatalogues : ${catalogue.size} cartes.\nSélection : ${selected.joinToString { it.title + " (" + it.references.joinToString { ref -> ref.provider } + ")" }}.\n\nLa présence au catalogue ne prouve pas le décodage vidéo Android.\n")
    }
    @Test fun cataloguesAndSaikiUseRealPublishedPages() = runBlocking {
        assumeTrue(System.getProperty("tvsama.networkAudit") == "true")
        val context = RuntimeEnvironment.getApplication()
        val application = StreamFlixApp()
        org.robolectric.util.ReflectionHelpers.callInstanceMethod<Void>(application, "attach",
            org.robolectric.util.ReflectionHelpers.ClassParameter.from(android.content.Context::class.java, context))
        StreamFlixApp::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, application)
        UserPreferences.setup(context)
        UserPreferences.setProviderCache(WiflixProvider, UserPreferences.PROVIDER_AUTOUPDATE, "false")
        val report = File("../verification/REQUESTED_SOURCES.md").apply {
            parentFile?.mkdirs()
            writeText("# Contrôles réseau — ${java.time.Instant.now()}\n\nLes réponses HTTP et les lecteurs publiés ne prouvent pas le décodage Android.\n\n")
        }
        val failures = mutableListOf<String>()
        for (provider in listOf(CoflixProvider, WarFlixProvider, MyFluneoProvider, WiflixProvider)) {
            try {
                withTimeout(65_000) {
                    val first = provider.getMovies(1).map { it.id } + provider.getTvShows(1).map { it.id }
                    val next = provider.getMovies(2).map { it.id } + provider.getTvShows(2).map { it.id }
                    assertTrue("${provider.name} : catalogue vide", first.isNotEmpty())
                    assertTrue("${provider.name} : pagination sans nouveaux titres", next.any { it !in first })
                    report.appendText("- ${provider.name} : ${first.size} cartes page 1 ; ${next.size} page 2, nouveaux identifiants confirmés.\n")
                }
            } catch (e: Exception) {
                failures += provider.name
                report.appendText("- ${provider.name} : ÉCHEC ${e.javaClass.simpleName} ${e.message.orEmpty().take(160)}\n")
            }
        }
        val manager = StreamFlixProviderManager.getInstance()
        val cards = MyFluneoProvider.search("saiki", 1).filterIsInstance<TvShow>()
        val matched = cards.filter { CatalogIdentity.title(CatalogIdentity.numberedBase(it.title)) == CatalogIdentity.title("The Disastrous Life of Saiki K.") }
        assertTrue("Saiki K. absent des alias du catalogue réel", matched.isNotEmpty())
        val details = MyFluneoProvider.getTvShow(matched.first().id)
        report.appendText("\nSaiki K. : ${matched.size} références MyFluneo reconnues ; ${details.seasons.size} saisons ; ${details.seasons.sumOf { it.episodes.size }} épisodes publiés.\n")
        assertTrue("Saiki K. : épisodes absents", details.seasons.any { it.episodes.isNotEmpty() })
        val merged = CatalogIdentity.merge(matched.map { Anime(it.title, "Série", emptyList(), it.id, provider = "MyFluneo") })
        assertEquals(1, merged.size)
        assertEquals(matched.size, merged.single().references.size)
        val entries = manager.refreshDirectory()
        report.appendText("\nAnnuaire : ${entries.size} entrées ; ${manager.directoryReport()}\n")
        report.appendText("\nÉchecs catalogue : ${failures.joinToString().ifBlank { "aucun" }}.\n")
        assertTrue("Catalogues en échec : $failures (voir verification/REQUESTED_SOURCES.md)", failures.isEmpty())
    }
}
