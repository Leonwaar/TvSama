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
    @Test fun frembedPublicCatalogAndPlayerReference() = runBlocking {
        assumeTrue(System.getProperty("tvsama.networkAudit") == "true")
        val application = com.streamflixreborn.streamflix.StreamFlixApp()
        org.robolectric.util.ReflectionHelpers.callInstanceMethod<Void>(application, "attach",
            org.robolectric.util.ReflectionHelpers.ClassParameter.from(android.content.Context::class.java, RuntimeEnvironment.getApplication()))
        com.streamflixreborn.streamflix.StreamFlixApp::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, application)
        UserPreferences.setup(RuntimeEnvironment.getApplication())
        val provider = FrembedProvider
        val report = File("../FREMBED_AUDIT.md")
        report.writeText("# Frembed — ${java.time.Instant.now()}\n\nAppels API et URL du lecteur public ; décodage Android à vérifier séparément.\n")
        val home = provider.getHome().flatMap { it.list }
        org.junit.Assert.assertTrue("Catalogue Frembed vide", home.isNotEmpty())
        val results = provider.search("Fight Club").filterIsInstance<Movie>()
        org.junit.Assert.assertTrue("Recherche Fight Club vide", results.any { it.id == "550" })
        val movie = provider.getMovie("550")
        org.junit.Assert.assertEquals("Fight Club", movie.title)
        val movieType = Video.Type.Movie(movie.id, movie.title, "1999", movie.poster.orEmpty(), movie.imdbId)
        val servers = provider.getServers(movie.id, movieType)
        org.junit.Assert.assertTrue("Aucun lecteur Frembed", servers.isNotEmpty())
        org.junit.Assert.assertTrue("Lecteur public Frembed absent", servers.any { it.src.contains("/embed/movie/550") })
        val show = provider.getTvShow("232022")
        val season = show.seasons.first { it.number == 1 }
        val episodes = provider.getEpisodesBySeason(season.id)
        org.junit.Assert.assertEquals((1..13).toList(), episodes.map { it.number })
        report.appendText("\nCatalogue : ${home.size} cartes. Recherche et fiche Fight Club : OK. Goldorak U : ${episodes.size} épisodes. Lecteurs film : ${servers.map { it.name }}.\n")
        var accessible = 0
        val nativeServers = servers.filter { it.id != "frembed-embed" }.take(5)
        for (server in nativeServers) {
            val result = try {
                withTimeout(12_000) {
                    val video = provider.getVideo(server)
                    val connection = URL(video.source).openConnection() as HttpURLConnection
                    try {
                        connection.connectTimeout = 5000
                        connection.readTimeout = 5000
                        video.headers.orEmpty().forEach { (key, value) -> connection.setRequestProperty(key, value) }
                        connection.setRequestProperty("Range", "bytes=0-1023")
                        val code = connection.responseCode
                        val bytes = if (code in 200..299) connection.inputStream.use { it.readNBytes(1024) } else byteArrayOf()
                        val mime = mediaMimeType(bytes)
                        var reason = ""
                        val validated = if (mime != null) validateLiveSource(VideoSource(server.name, video.source,
                            "UNKNOWN", "Auto", provider.name, video.headers.orEmpty())) { reason = it } else null
                        if (validated != null) accessible++
                        "HTTP $code, conteneur ${mime ?: "absent"}, segments ${if (validated != null) "accessibles" else "échec $reason"}"
                    } finally { connection.disconnect() }
                }
            } catch (e: Exception) { "Échec ${e.javaClass.simpleName}: ${e.message.orEmpty().take(100)}" }
            report.appendText("- ${server.name}: $result\n")
        }
        report.appendText("Médias accessibles : $accessible/${nativeServers.size}. Lecture décodée : non vérifiée.\n")
    }
    @Test fun nativeFrenchLivePlayback() = runBlocking {
        assumeTrue(System.getProperty("tvsama.networkAudit") == "true")
        val application = com.streamflixreborn.streamflix.StreamFlixApp()
        org.robolectric.util.ReflectionHelpers.callInstanceMethod<Void>(application, "attach",
            org.robolectric.util.ReflectionHelpers.ClassParameter.from(android.content.Context::class.java, RuntimeEnvironment.getApplication()))
        com.streamflixreborn.streamflix.StreamFlixApp::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, application)
        UserPreferences.setup(RuntimeEnvironment.getApplication())
        val report = File("../LIVE_NATIVE_AUDIT.md")
        report.writeText("# Direct TV français — ${java.time.Instant.now()}\n\nContrôle du flux HLS et de son segment, sans décodage Android.\n")
        val events = FrenchLiveTv.events(force = true)
        val event = events.firstOrNull { it.title.contains("Arte", true) } ?: events.first()
        val readers = FrenchLiveTv.servers(event.url)
        val checked = readers.firstNotNullOfOrNull { reader ->
            validateLiveSource(VideoSource(reader.name, reader.src, "VF", "Auto", "TVradioZap"))
        }
        report.appendText("\n${event.title} : ${readers.size} adresse(s) publiées, flux et segment ${if (checked != null) "accessibles" else "non confirmés"}.\n")
        org.junit.Assert.assertNotNull("Aucun flux HLS français confirmé pour ${event.title}", checked)
    }
    @Test fun movixWorksWithoutPersonalMetadataKey() = runBlocking {
        assumeTrue(System.getProperty("tvsama.networkAudit") == "true")
        val application = com.streamflixreborn.streamflix.StreamFlixApp()
        org.robolectric.util.ReflectionHelpers.callInstanceMethod<Void>(application, "attach",
            org.robolectric.util.ReflectionHelpers.ClassParameter.from(android.content.Context::class.java, RuntimeEnvironment.getApplication()))
        com.streamflixreborn.streamflix.StreamFlixApp::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, application)
        UserPreferences.setup(RuntimeEnvironment.getApplication())
        val previousKey = UserPreferences.tmdbApiKey
        UserPreferences.tmdbApiKey = ""
        val report = File("../MOVIX_AUDIT.md")
        report.writeText("# Movix sans clé personnelle — ${java.time.Instant.now()}\n\nMétadonnées publiques TMDB, API de lecteurs Movix. Aucun décodage Android dans cet essai.\n")
        try {
            val movies = MovixProvider.getMovies(1)
            val next = MovixProvider.getMovies(2)
            org.junit.Assert.assertTrue(movies.isNotEmpty())
            org.junit.Assert.assertTrue(next.isNotEmpty() && next.any { m -> movies.none { it.id == m.id } })
            report.appendText("\nFilms : ${movies.size} en page 1, ${next.size} en page 2 ; nouveaux identifiants vérifiés.\n")
            val results = MovixProvider.search("Classroom of the Elite").filterIsInstance<TvShow>()
            val card = results.first { it.id == "72517" }
            val show = MovixProvider.getTvShow(card.id)
            val episodes = MovixProvider.getEpisodesBySeason(show.seasons.first { it.number == 1 }.id)
            org.junit.Assert.assertEquals((1..12).toList(), episodes.map { it.number })
            report.appendText("Classroom : recherche ${results.size} résultat(s), ${show.seasons.size} saisons annoncées, S1 : ${episodes.size} épisodes.\n")
            val film = MovixProvider.getMovie("550")
            org.junit.Assert.assertEquals("Fight Club", film.title)
            val servers = MovixProvider.getServers(film.id, Video.Type.Movie(film.id, film.title, "1999", film.poster.orEmpty(), film.imdbId))
            org.junit.Assert.assertTrue(servers.isNotEmpty())
            val direct = servers.first { it.src.contains(".mp4") }
            val video = MovixProvider.getVideo(direct)
            val checked = validateLiveSource(VideoSource(direct.name, video.source, "UNKNOWN", "Auto", "Movix", video.headers.orEmpty()))
            org.junit.Assert.assertNotNull("Média Movix inaccessible", checked)
            report.appendText("Fight Club : fiche réelle, ${servers.size} lecteurs, MP4 accessible ; langue non vérifiée.\n")
        } finally { UserPreferences.tmdbApiKey = previousKey }
    }

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
            val counts = loaded.episodes.distinctBy { it.seasonNumber to it.number }.groupingBy { it.seasonNumber }.eachCount()
            org.junit.Assert.assertEquals(mapOf(1 to 24, 2 to 13, 3 to 13), counts.filterKeys { it in 1..3 })
            org.junit.Assert.assertTrue(counts.filterKeys { it > 3 }.values.all { it > 0 })
            report.appendText("\nRésolution TvSama depuis une ancienne fiche saison 3 : une fiche, saisons et nombres d’épisodes $counts.\n")
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
        val report = File("../CLASSROOM_AUDIT.md")
        report.writeText("# Classroom VF — ${java.time.Instant.now()}\n\nRecherche : ${search.size} fiches ; ${show.seasons.size} saisons ; ${vf.size} serveurs VF.\n")
        for (server in vf) {
            val result = try {
                withTimeout(18_000) {
                    val video = provider.getVideo(server)
                    val conn = URL(video.source).openConnection() as HttpURLConnection
                    try {
                        conn.connectTimeout = 5000; conn.readTimeout = 5000
                        video.headers.orEmpty().forEach { (k, v) -> conn.setRequestProperty(k, v) }
                        conn.setRequestProperty("Range", "bytes=0-1023")
                        check(conn.responseCode in 200..299) { "HTTP ${conn.responseCode}" }
                        check(mediaMimeType(conn.inputStream.use { it.readNBytes(1024) }) != null) { "Pas de conteneur média" }
                        "Média accessible"
                    } finally { conn.disconnect() }
                }
            } catch (e: Exception) { "Échec : ${e.javaClass.simpleName} ${e.message.orEmpty().substringBefore("http").take(120)}" }
            report.appendText("- ${server.name} : $result\n")
        }
        val manager = StreamFlixProviderManager.getInstance()
        val loaded = manager.loadDetails(Anime(show.title, "Série", emptyList(), show.id, provider = provider.name))
        val resolution = async(Dispatchers.IO) { withTimeoutOrNull(70_000) {
            manager.resolveSources(loaded, loaded.episodes.first { it.seasonNumber == 1 && it.number == 1 }, "VF", refresh = true)
        } }
        while (!resolution.isCompleted) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            delay(10)
        }
        val sources = resolution.await()
        report.appendText("\nRésolution TvSama : ${sources?.count { it.reachable } ?: "délai dépassé à 70 s"} sources accessibles, sans décodage matériel.\n")
        sources.orEmpty().forEach { report.appendText("- ${it.provider} · ${it.name} · ${it.language} · ${it.mimeType.orEmpty()}\n") }
        org.junit.Assert.assertNotNull("Résolution TvSama > 70 s", sources)
        org.junit.Assert.assertTrue("Sources: ${sources.orEmpty().map { Triple(it.name, it.language, it.reachable) }}; statuts: ${manager.providerStatuses()}", sources.orEmpty().any { it.language == "VF" && it.reachable })
        org.junit.Assert.assertTrue(sources.orEmpty().all { it.language in setOf("VF", "UNKNOWN") })
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
        report.writeText("# Audit des sources — ${java.time.Instant.now()}\n\nAppels réels aux adaptateurs dans une JVM Android simulée. Signature média et, pour HLS, segment vérifiés. Aucun décodage vidéo ni essai matériel. Un échantillon par source.\n\n| Source | Catalogue | Recherche | Fiche / épisodes | Extraction / média |\n|---|---|---|---|---|\n")
        val manager = StreamFlixProviderManager.getInstance()
        val directoryResult = runCatching { manager.refreshDirectory() }
        val directoryStatus = directoryResult.fold(
            { "${it.size} entrées françaises analysées" },
            { "indisponible (${it.javaClass.simpleName})" },
        )
        report.appendText("| Annuaire | $directoryStatus | — | — | — |\n")
        val evidence = File("../verification/source-audit.jsonl").apply { parentFile?.mkdirs(); writeText("") }
        for (provider in Provider.providers.keys.filter { it.language == "fr" }) {
            val observedAt = java.time.Instant.now().toString()
            val stageTimes = mutableListOf<Long>()
            var chosenServer: String? = null
            var seasonNumber: Int? = null
            var episodeNumber: Int? = null
            val results = mutableListOf<String>()
            suspend fun <T> stage(timeoutMs: Long = 30_000, block: suspend () -> T): T? {
                val started = System.nanoTime()
                return try {
                coroutineScope {
                    val task = async(Dispatchers.IO) { withTimeout(timeoutMs) { block() } }
                    // Browser extractors dispatch to Android's main looper. Pump it during JVM audits
                    // so their timeouts can finish even though JavaScript itself is not simulated.
                    while (!task.isCompleted) {
                        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                        delay(10)
                    }
                    task.await()
                }.also { results += "OK" }
            } catch (_: TimeoutCancellationException) { results += "Délai dépassé"; null }
            catch (e: Exception) { results += e.javaClass.simpleName + ": " + e.message.orEmpty().replace(Regex("https?://[^ ]+"), "[URL]").replace("|", "/").replace("\n", " ").take(120); null }
                finally { stageTimes += (System.nanoTime() - started) / 1_000_000 }
            }
            val home = stage { provider.getHome().flatMap { it.list }.filter { it is Movie || it is TvShow }.also { check(it.isNotEmpty()) { "Vide" } } }
            // A catalogue may lead with an announced/ongoing title whose episode list is
            // intentionally empty. Prefer a published sample for the detail/media stages.
            val sample = when (provider.name) {
                "Animes-Sama" -> TvShow("https://animes-sama.su/anime/one-piece/", "One Piece")
                "Voiranime" -> TvShow("https://voir-anime.to/anime/tokyo-revengers-3/", "Tokyo Revengers")
                "MyFluneo" -> TvShow("https://myfluneo.eu/anime/naruto/", "Naruto")
                else -> home?.firstOrNull()
            }
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
                        val season = tv.seasons.firstOrNull { it.number > 0 } ?: tv.seasons.firstOrNull()
                            ?: error("Aucune saison publiée pour ${tv.title}")
                        val episodes = season.episodes.ifEmpty { provider.getEpisodesBySeason(season.id) }
                        val ep = episodes.firstOrNull { it.number > 0 } ?: episodes.firstOrNull()
                            ?: error("Aucun épisode publié pour ${tv.title}, saison ${season.number}")
                        id = ep.id
                        seasonNumber = season.number
                        episodeNumber = ep.number
                        Video.Type.Episode(ep.id, ep.number, ep.title, ep.poster, ep.overview,
                            Video.Type.Episode.TvShow(tv.id, tv.title, tv.poster, tv.banner, null, tv.imdbId), Video.Type.Episode.Season(season.number, season.title))
                    }
                    else -> error("Pas de fiche accessible")
                }
            }
            if (type != null && id != null) {
                stage(timeoutMs = 45_000) {
                    val servers = provider.getServers(id!!, type)
                    check(servers.isNotEmpty())
                    var success = false
                    val failures = mutableListOf<String>()
                    val nativeServers = servers.filterNot { it.id == "frembed-embed" }
                        .filter { provider.name != "WarFlix" || mediaLanguage(it.name, emptyList()) in listOf("VF", "VOSTFR") }
                    check(nativeServers.isNotEmpty()) { "Lecteur navigateur seulement ; audit JVM impossible" }
                    for (server in nativeServers) {
                        try {
                            val video = withContext(com.streamflixreborn.streamflix.utils.ExtractionProvider(provider)) {
                                withTimeout(if (provider.name == "Coflix") 24_000 else 12_000) { provider.getVideo(server) }
                            }
                            var reason = ""
                            val verified = withTimeout(9_000) {
                                validateLiveSource(VideoSource(server.name, video.source, "UNKNOWN", "Auto", provider.name,
                                    video.headers.orEmpty()), minimumVodDurationMs = if (provider is IptvProvider) 0 else 90_000) { reason = it }
                            }
                            check(verified != null) { reason.ifBlank { "Signature média absente" } }
                            chosenServer = server.name
                            success = true; break
                        } catch (e: Exception) {
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            failures += server.name + ":" + (e.message ?: e.javaClass.simpleName)
                                .replace(Regex("https?://[^ ]+"), "[URL]").take(140)
                        }
                    }
                    check(success) { "Aucun média vérifié (${failures.joinToString()})" }
                }
            } else results += "Non testable"
            report.appendText("| ${provider.name} | ${results.joinToString(" | ")} |\n")
            val reference = when (sample) { is Movie -> sample.id; is TvShow -> sample.id; else -> "" }
            // Hash public IDs so the trace remains stable without retaining query credentials.
            val referenceHash = java.security.MessageDigest.getInstance("SHA-256")
                .digest(reference.toByteArray()).joinToString("") { "%02x".format(it) }
            evidence.appendText(com.google.gson.Gson().toJson(linkedMapOf(
                "at" to observedAt, "version" to BuildConfig.VERSION_NAME, "versionCode" to BuildConfig.VERSION_CODE,
                "environment" to "Robolectric API 28 — no video decoding", "source" to provider.name,
                "title" to title, "referenceSha256" to referenceHash, "season" to seasonNumber,
                "episode" to episodeNumber, "requestedLanguage" to "unfiltered", "selectedServer" to chosenServer,
                "stages" to listOf("catalogue", "search", "details", "media").mapIndexed { index, name ->
                    mapOf("name" to name, "result" to results.getOrNull(index), "durationMs" to stageTimes.getOrNull(index))
                }, "decoded" to false,
            )) + "\n")
            println("AUDIT ${provider.name} [${provider.baseUrl}]: ${results.joinToString()}")
        }
        for (name in manager.getProviderNames().filter { manager.externalSource(it) != null }) {
            val external = manager.externalSource(name)!!
            report.appendText("| $name (lien externe) | Non testé : ${external.url} | Pas d’adaptateur | Pas d’adaptateur | Non intégrée |\n")
        }
        val live = runCatching { StreamedLive.events(force = true) }
        report.appendText("\nStreamed : ${live.getOrNull()?.let { "${it.size} événements, ${it.count { e -> e.isLive }} en direct" } ?: "calendrier indisponible"}.\n")
        live.getOrNull()?.firstOrNull { it.isLive }?.let { event ->
            val readers = runCatching { StreamedLive.servers(event.url) }.getOrDefault(emptyList())
            report.appendText("Lecteurs publiés pour ${event.title} : ${readers.size} · média non confirmé dans cet échantillon.\n")
        }
        val metadata = enrichSeries(Anime("Jujutsu Kaisen", "Série", emptyList()))
        report.appendText("\nMétadonnées Jujutsu Kaisen : IMDb ${metadata.imdbId}, première diffusion ${metadata.firstAired}, ${metadata.seriesStatus}.\n")
        val intro = fetchIntro("tt0944947", 1, 1)
        report.appendText("\nIntroDB (tt0944947 S1E1) : ${intro ?: "aucun segment disponible"}.\n")
    }
}
