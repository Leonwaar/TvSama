package fr.nekotv

import android.app.Application
import com.streamflixreborn.streamflix.providers.*
import com.streamflixreborn.streamflix.models.Movie
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.utils.UserPreferences
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Explicit integration audit: availability failures are recorded, never disguised as playback success. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class SourceNetworkAuditTest {

    @Test fun tokyoSeasonsAndEpisodes() = runBlocking {
        assumeTrue(System.getProperty("tvsama.networkAudit") == "true")
        val application = com.streamflixreborn.streamflix.StreamFlixApp()
        org.robolectric.util.ReflectionHelpers.callInstanceMethod<Void>(application, "attach",
            org.robolectric.util.ReflectionHelpers.ClassParameter.from(android.content.Context::class.java, RuntimeEnvironment.getApplication()))
        com.streamflixreborn.streamflix.StreamFlixApp::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, application)
        UserPreferences.setup(RuntimeEnvironment.getApplication())
        val report = File("../SEASONS_AUDIT.md")
        report.writeText("# Saisons — ${java.time.Instant.now()}\n\nAppels réels aux adaptateurs, sans décodage vidéo.\n")
        for (provider in listOf(AnimeSamaProvider, AnimesSamaProvider, VoirAnimeProvider)) {
            val results = provider.search("Tokyo Revengers").filterIsInstance<TvShow>()
            org.junit.Assert.assertTrue("${provider.name}: recherche vide", results.isNotEmpty())
            val cards = CatalogIdentity.merge(results.map { Anime(it.title, "Série", emptyList(), it.id, provider = provider.name) })
            val card = cards.first { it.title == "Tokyo Revengers" }
            val numbers = mutableSetOf<Pair<Int, Int>>()
            for (ref in card.references) {
                val tv = provider.getTvShow(ref.id)
                for (season in tv.seasons) {
                    val eps = provider.getEpisodesBySeason(season.id)
                    val number = if (tv.seasons.size == 1) CatalogIdentity.seasonNumber(tv.title, card.title) ?: season.number else season.number
                    numbers += eps.map { number to it.number }
                    report.appendText("- ${provider.name} : ${tv.title}, saison $number : ${eps.size} épisodes.\n")
                }
            }
            org.junit.Assert.assertTrue("${provider.name}: S1 incomplète $numbers", (1..24).all { (1 to it) in numbers })
            org.junit.Assert.assertTrue("${provider.name}: S2 incomplète", (1..13).all { (2 to it) in numbers })
            org.junit.Assert.assertTrue("${provider.name}: S3 incomplète", (1..13).all { (3 to it) in numbers })
        }
        val manager = StreamFlixProviderManager.getInstance()
        val active = setOf("Voiranime", "Anime-Sama", "Animes-Sama")
        val states = manager.getProviderNames().associateWith { manager.isProviderEnabled(it) }
        try {
            states.keys.forEach { manager.setProviderEnabled(it, it in active) }
            val loaded = manager.loadDetails(Anime("Tokyo Revengers 3", "Série", emptyList(),
                "https://voir-anime.to/anime/tokyo-revengers-3/", provider = "Voiranime"))
            org.junit.Assert.assertEquals("Tokyo Revengers", loaded.title)
            org.junit.Assert.assertEquals(mapOf(1 to 24, 2 to 13, 3 to 13),
                loaded.episodes.distinctBy { it.seasonNumber to it.number }.groupingBy { it.seasonNumber }.eachCount())
            report.appendText("\nRésolution TvSama depuis une ancienne fiche saison 3 : une fiche, 50 épisodes uniques, trois saisons.\n")
        } finally { states.forEach { (name, enabled) -> manager.setProviderEnabled(name, enabled) } }
    }

    @Test fun animeUltimePlayback() = runBlocking {
        assumeTrue(System.getProperty("tvsama.networkAudit") == "true")
        val application = com.streamflixreborn.streamflix.StreamFlixApp()
        org.robolectric.util.ReflectionHelpers.callInstanceMethod<Void>(application, "attach",
            org.robolectric.util.ReflectionHelpers.ClassParameter.from(android.content.Context::class.java, RuntimeEnvironment.getApplication()))
        com.streamflixreborn.streamflix.StreamFlixApp::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, application)
        UserPreferences.setup(RuntimeEnvironment.getApplication())
        val provider = AnimeUltimeProvider
        val catalogue = provider.getHome().flatMap { it.list }
        org.junit.Assert.assertTrue(catalogue.isNotEmpty())
        val show = provider.search("Great Mazinger").filterIsInstance<TvShow>().first { it.title == "Great Mazinger" }
        val details = provider.getTvShow(show.id)
        val episodes = details.seasons.single().episodes
        org.junit.Assert.assertEquals((1..56).toList(), episodes.map { it.number }.distinct())
        val ep = episodes.first()
        val type = Video.Type.Episode(ep.id, ep.number, ep.title, null, null,
            Video.Type.Episode.TvShow(show.id, show.title, null, null, null, null), Video.Type.Episode.Season(1, "Saison 1"))
        val servers = provider.getServers(ep.id, type)
        org.junit.Assert.assertTrue(servers.isNotEmpty())
        val video = provider.getVideo(servers.first())
        val connection = URL(video.source).openConnection() as HttpURLConnection
        val result = try {
            connection.connectTimeout = 10000; connection.readTimeout = 10000
            video.headers.orEmpty().forEach { (key, value) -> connection.setRequestProperty(key, value) }
            connection.setRequestProperty("Range", "bytes=0-1023")
            check(connection.responseCode in 200..299) { "Média HTTP ${connection.responseCode}" }
            check(!connection.contentType.orEmpty().contains("text/html")) { "Réponse HTML à la place du média" }
            val header = connection.inputStream.use { it.readNBytes(12) }
            check(header.size == 12 && String(header, 4, 4, Charsets.US_ASCII) == "ftyp") { "En-tête MP4 absent (${connection.contentType})" }
            "HTTP ${connection.responseCode} ${connection.contentType}"
        } finally { connection.disconnect() }
        File("../ANIME_ULTIME_AUDIT.md").writeText("# Anime-Ultime — ${java.time.Instant.now()}\n\nCatalogue : ${catalogue.size} fiches. Recherche Great Mazinger : OK.\n56 épisodes vérifiés ; épisode 1 : ${servers.size} lecteur(s), $result.\nAccès média vérifié, sans décodage vidéo matériel.\n")
    }

    @Test fun classroomFrenchPlayback() = runBlocking {
        assumeTrue(System.getProperty("tvsama.networkAudit") == "true")
        val application = com.streamflixreborn.streamflix.StreamFlixApp()
        org.robolectric.util.ReflectionHelpers.callInstanceMethod<Void>(application, "attach",
            org.robolectric.util.ReflectionHelpers.ClassParameter.from(android.content.Context::class.java, RuntimeEnvironment.getApplication()))
        com.streamflixreborn.streamflix.StreamFlixApp::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, application)
        UserPreferences.setup(RuntimeEnvironment.getApplication())
        val provider = AnimesSamaProvider
        val search = provider.search("classroom")
        org.junit.Assert.assertTrue(search.isNotEmpty())
        val show = provider.getTvShow("https://animes-sama.su/anime/classroom-of-the-elite/")
        org.junit.Assert.assertTrue(show.seasons.size >= 3)
        val ep = provider.getEpisodesBySeason(show.seasons.first { it.number == 1 }.id).first { it.number == 1 }
        val type = Video.Type.Episode(ep.id, 1, ep.title, null, null,
            Video.Type.Episode.TvShow(show.id, show.title, null, null, null, null), Video.Type.Episode.Season(1, "Saison 1"))
        val servers = provider.getServers(ep.id, type)
        val vf = servers.filter { it.name.startsWith("VF ·") }
        org.junit.Assert.assertTrue(vf.isNotEmpty())
        val sibnet = vf.first { it.name.endsWith("VF-2") }
        val video = provider.getVideo(sibnet)
        org.junit.Assert.assertTrue(video.source.contains("4799425"))
        val connection = URL(video.source).openConnection() as HttpURLConnection
        val result = try {
            connection.connectTimeout = 15000; connection.readTimeout = 15000
            video.headers.orEmpty().forEach { (k, v) -> connection.setRequestProperty(k, v) }
            connection.setRequestProperty("Range", "bytes=0-1023")
            val code = connection.responseCode
            check(code in 200..299) { "Sibnet média HTTP $code" }
            check(!connection.contentType.orEmpty().contains("text/html"))
            check(connection.inputStream.use { it.read() } >= 0)
            "HTTP $code, ${connection.contentType}"
        } finally { connection.disconnect() }
        val manager = StreamFlixProviderManager.getInstance()
        val loaded = manager.loadDetails(Anime(show.title, "Série", emptyList(), show.id, provider = provider.name))
        val sources = manager.resolveSources(loaded, loaded.episodes.first { it.seasonNumber == 1 && it.number == 1 }, "VF", refresh = true)
        org.junit.Assert.assertTrue("Sources: ${sources.map { Triple(it.name, it.language, it.reachable) }}; statuts: ${manager.providerStatuses()}", sources.any { it.language == "VF" && it.url.contains("4799425") && it.reachable })
        org.junit.Assert.assertTrue(sources.all { it.language == "VF" })
        File("../CLASSROOM_AUDIT.md").writeText("# Classroom VF — ${java.time.Instant.now()}\n\nRecherche : ${search.size} fiches ; ${show.seasons.size} saisons ; ${vf.size} serveurs VF.\nSibnet 4799425 : $result, accès média confirmé (sans décodage matériel).\nRésolution TvSama : ${sources.size} sources, toutes VF, Sibnet joignable.\n")
    }
    @Test fun liveAndIntro() = runBlocking {
        assumeTrue(System.getProperty("tvsama.networkAudit") == "true")
        val report = File("../LIVE_AUDIT.md")
        val events = VolkaMaxProvider.events()
        report.writeText("# Contrôle directs et IntroDB — ${java.time.Instant.now()}\n\n${events.size} programmes, ${events.count { it.isLive }} actifs.\n")
        val event = events.firstOrNull { it.isLive }
        if (event != null) {
            val servers = VolkaMaxProvider.servers(event.url)
            report.appendText("\n${event.title} : ${servers.size} lecteurs proposés.\n")
            servers.forEach { report.appendText("- ${it.name}\n") }
        }
        val intro = fetchIntro("tt12343534", 1, 1)
        report.appendText("\nIntroDB Jujutsu Kaisen S1E1 : $intro\n")
        org.junit.Assert.assertNotNull(intro)
        org.junit.Assert.assertTrue(events.isNotEmpty())
    }
    @Test fun allFrenchSources() = runBlocking {
        assumeTrue(System.getProperty("tvsama.networkAudit") == "true")
        val application = com.streamflixreborn.streamflix.StreamFlixApp()
        org.robolectric.util.ReflectionHelpers.callInstanceMethod<Void>(application, "attach",
            org.robolectric.util.ReflectionHelpers.ClassParameter.from(android.content.Context::class.java, RuntimeEnvironment.getApplication()))
        com.streamflixreborn.streamflix.StreamFlixApp::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, application)
        UserPreferences.setup(RuntimeEnvironment.getApplication())
        val report = File("../SOURCE_AUDIT.md")
        report.writeText("# Audit des sources — ${java.time.Instant.now()}\n\nAppels réels aux adaptateurs dans une JVM Android simulée. Aucun décodage vidéo ni essai matériel. Un échantillon par source.\n\n| Source | Catalogue | Recherche | Fiche / épisodes | Extraction / média |\n|---|---|---|---|---|\n")
        val directory = StreamFlixProviderManager.getInstance().refreshDirectory()
        report.appendText("| Annuaire | ${directory.size} entrées françaises analysées | — | — | — |\n")
        for (provider in Provider.providers.keys.filter { it.language == "fr" }) {
            val results = mutableListOf<String>()
            suspend fun <T> stage(block: suspend () -> T): T? = try {
                withTimeout(30_000) { withContext(Dispatchers.IO) { block() } }.also { results += "OK" }
            } catch (_: TimeoutCancellationException) { results += "Délai dépassé"; null }
            catch (e: Exception) { results += e.javaClass.simpleName + ": " + e.message.orEmpty().replace(Regex("https?://[^ ]+"), "[URL]").replace("|", "/").replace("\n", " ").take(120); null }
            val home = stage { provider.getHome().flatMap { it.list }.filter { it is Movie || it is TvShow }.also { check(it.isNotEmpty()) { "Vide" } } }
            val sample = home?.firstOrNull()
            val title = when (sample) { is Movie -> sample.title; is TvShow -> sample.title; else -> "Naruto" }
            stage {
                var found = provider.search(title.take(70))
                if (found.isEmpty()) found = provider.search(title.split(" ").take(2).joinToString(" "))
                check(found.isNotEmpty()) { "Aucun résultat pour $title" }
            }
            var id: String? = null
            val type = stage {
                when (sample) {
                    is Movie -> provider.getMovie(sample.id).let { id = it.id; Video.Type.Movie(it.id, it.title, "", it.poster.orEmpty(), it.imdbId) }
                    is TvShow -> {
                        val tv = provider.getTvShow(sample.id)
                        val season = tv.seasons.first()
                        val ep = (season.episodes.ifEmpty { provider.getEpisodesBySeason(season.id) }).first()
                        id = ep.id
                        Video.Type.Episode(ep.id, ep.number, ep.title, ep.poster, ep.overview,
                            Video.Type.Episode.TvShow(tv.id, tv.title, tv.poster, tv.banner, null, tv.imdbId), Video.Type.Episode.Season(season.number, season.title))
                    }
                    else -> error("Pas de fiche accessible")
                }
            }
            if (type != null && id != null) {
                stage {
                    UserPreferences.currentProvider = provider
                    val servers = provider.getServers(id!!, type)
                    check(servers.isNotEmpty())
                    var success = false
                    val failures = mutableListOf<String>()
                    for (server in servers.take(3)) {
                        try {
                            val video = withTimeout(8_000) { provider.getVideo(server) }
                            val conn = URL(video.source).openConnection() as HttpURLConnection
                            try {
                                conn.connectTimeout = 5_000; conn.readTimeout = 5_000
                                video.headers.orEmpty().forEach { (k, v) -> conn.setRequestProperty(k, v) }
                                conn.setRequestProperty("Range", "bytes=0-1023")
                                check(conn.responseCode in 200..299)
                                check(!conn.contentType.orEmpty().contains("text/html"))
                                check(conn.inputStream.use { it.read() } >= 0)
                                success = true; break
                            } finally { conn.disconnect() }
                        } catch (e: Exception) { failures += server.name + ":" + e.javaClass.simpleName }
                    }
                    check(success) { "Aucun média vérifié (${failures.joinToString()})" }
                }
            } else results += "Non testable"
            report.appendText("| ${provider.name} | ${results.joinToString(" | ")} |\n")
            println("AUDIT ${provider.name} [${provider.baseUrl}]: ${results.joinToString()}")
        }
        for ((name, url) in listOf("Neko-Sama" to "https://animes-sama.su/", "FRAnime" to "https://franime.fr/", "AnimeOVF" to "https://animeovf.fr/", "Coflix" to "https://coflix.ac/")) {
            val state = withContext(Dispatchers.IO) { runCatching {
                val c = URL(url).openConnection() as HttpURLConnection
                try { c.connectTimeout = 5000; c.readTimeout = 5000; "Site HTTP ${c.responseCode}" } finally { c.disconnect() }
            }.getOrElse { it.javaClass.simpleName } }
            report.appendText("| $name (lien externe) | $state | Pas d’adaptateur | Pas d’adaptateur | Non intégrée |\n")
        }
        val live = runCatching { VolkaMaxProvider.events() }
        report.appendText("\nVolkamax : ${live.getOrNull()?.let { "${it.size} événements, ${it.count { e -> e.isLive }} actifs" } ?: "échec du calendrier"}.\n")
        live.getOrNull()?.firstOrNull { it.isLive }?.let { event ->
            val liveMedia = runCatching {
                val servers = VolkaMaxProvider.servers(event.url)
                val video = servers.firstNotNullOfOrNull { server -> runCatching { VolkaMaxProvider.video(server) }.getOrNull() }
                check(video != null) { "Aucun extracteur compatible parmi ${servers.size} lecteurs" }
                val c = URL(video.source).openConnection() as HttpURLConnection
                try { c.connectTimeout = 5000; c.readTimeout = 5000; video.headers.orEmpty().forEach { (k,v) -> c.setRequestProperty(k,v) }; check(c.responseCode in 200..299); check(c.inputStream.use { it.read() } >= 0) } finally { c.disconnect() }
            }
            report.appendText("\nVolkamax — accès média : ${if (liveMedia.isSuccess) "OK (sans décodage)" else liveMedia.exceptionOrNull()?.javaClass?.simpleName}.\n")
        }
        val metadata = enrichSeries(Anime("Jujutsu Kaisen", "Série", emptyList()))
        report.appendText("\nMétadonnées Jujutsu Kaisen : IMDb ${metadata.imdbId}, première diffusion ${metadata.firstAired}, ${metadata.seriesStatus}.\n")
        val intro = fetchIntro("tt0944947", 1, 1)
        report.appendText("\nIntroDB (tt0944947 S1E1) : ${intro ?: "aucun segment disponible"}.\n")
    }
}
