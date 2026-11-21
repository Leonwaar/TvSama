package fr.nekotv

import android.app.Application
import android.content.Context
import com.streamflixreborn.streamflix.StreamFlixApp
import com.streamflixreborn.streamflix.providers.MyFluneoProvider
import com.streamflixreborn.streamflix.providers.WarFlixProvider
import com.streamflixreborn.streamflix.providers.WiflixProvider
import com.streamflixreborn.streamflix.providers.WordPressStreamingProvider
import com.streamflixreborn.streamflix.providers.SourceAddressValidation
import com.streamflixreborn.streamflix.utils.SourceAddresses
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlinx.coroutines.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class SourceAddressRecoveryTest {
    @Test fun parallelExtractionsKeepTheirProviderAcrossDispatcherChanges() = runBlocking {
        val providers = com.streamflixreborn.streamflix.providers.Provider.providers.keys.take(2)
        val ready = kotlinx.coroutines.channels.Channel<Unit>(2)
        val release = CompletableDeferred<Unit>()
        val jobs = providers.map { provider -> async(Dispatchers.Default + com.streamflixreborn.streamflix.utils.ExtractionProvider(provider)) {
            ready.send(Unit); release.await()
            withContext(Dispatchers.IO) { com.streamflixreborn.streamflix.utils.extractionProvider()?.name }
        } }
        withTimeout(2000) { repeat(2) { ready.receive() } }
        release.complete(Unit)
        assertEquals(providers.map { it.name }, jobs.awaitAll())
    }
    @Test fun protectedNewAddressRemainsAccessibleWithoutReplacingTheWorkingAdapter() {
        val addresses = SourceAddresses
        addresses.update("Coflix", "https://old.example/", "https://working.example/")
        addresses.rememberPending("Coflix", "https://captcha.example/")
        assertEquals("https://working.example/", addresses.current("Coflix", ""))
        assertEquals("https://captcha.example/", StreamFlixProviderManager.getInstance().accessUrl("Coflix"))
        addresses.rememberPending("Coflix", "http://unsafe.example/")
        assertEquals("https://captcha.example/", addresses.pending("Coflix"))
        addresses.update("Coflix", "https://working.example/", "https://captcha.example/")
        org.junit.Assert.assertNull(addresses.pending("Coflix"))
        assertEquals("https://captcha.example/film/42", addresses.rewrite("https://old.example/film/42"))
    }
    @Test fun removedVavooIsAbsentAndTheThreePriorityProvidersRemainNative() {
        val names = com.streamflixreborn.streamflix.providers.Provider.providers.keys.map { it.name }
        org.junit.Assert.assertFalse(names.any { it.contains("vavoo", true) })
        assertTrue(names.containsAll(listOf("Wiflix", "Papadustream", "FRAnime")))
    }
    @Test fun repeatedDirectorySlugSelectsTheHealthiestRecordInsteadOfNone() {
        val mirror = DirectorySource("Wiflix", "wiflix", "https://down.example/", "down")
        val proposed = DirectorySource("Wiflix", "wiflix", "https://wiki.example/", "candidate")
        val moved = DirectorySource("Wiflix", "wiflix", "https://moved.example/", "redirected")
        val active = DirectorySource("Wiflix", "wiflix", "https://live.example/", "active")
        val aliases = mapOf("Wiflix" to "wiflix")
        assertEquals("https://live.example/", selectDirectoryEntry(listOf(mirror, proposed, moved, active), aliases, "Wiflix")?.url)
        assertEquals("https://moved.example/", selectDirectoryEntry(listOf(mirror, proposed, moved), aliases, "Wiflix")?.url)
        assertEquals("https://wiki.example/", selectDirectoryEntry(listOf(mirror, proposed), aliases, "Wiflix")?.url)
        assertEquals("https://down.example/", selectDirectoryEntry(listOf(mirror), aliases, "Wiflix")?.url)
    }
    @Test fun directoryEntryFallsBackToItsNameWhenTheSlugDiffers() {
        val renamed = DirectorySource("Voiranime", "vostfr-voiranime", "https://voiranime.example/", "active")
        val aliases = mapOf("Voiranime" to "voiranime")
        assertEquals(renamed, selectDirectoryEntry(listOf(renamed), aliases, "Voiranime"))
        org.junit.Assert.assertNull(selectDirectoryEntry(listOf(renamed), aliases, "Wiflix"))
    }
    @Test fun providerAliasesReachTheirDirectorySlugs() {
        val entries = listOf(
            DirectorySource("Coflix", "coflix", "https://coflix.example/", "active"),
            DirectorySource("Movix", "movix", "https://movix.example/", "active"),
            DirectorySource("Kidraz", "kidraz", "https://kidraz.example/", "active"),
            DirectorySource("AnimeKO", "animeko", "https://animeko.example/", "active"))
        val aliases = mapOf("Coflix" to "coflix", "Movix" to "movix", "Kidraz" to "kidraz", "AnimeKO" to "animeko")
        for (name in listOf("Coflix", "Movix", "Kidraz", "AnimeKO"))
            assertEquals(name, selectDirectoryEntry(entries, aliases, name)?.name)
    }
    @Test fun franimeCaptchaRetryKeepsTheExactPlayerPageAndRefreshClearsIt() = kotlinx.coroutines.runBlocking {
        val provider = com.streamflixreborn.streamflix.providers.FrAnimeProvider
        val field = provider.javaClass.getDeclaredField("playerPages").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val pages = field.get(provider) as MutableMap<String, Pair<Long, String>>
        val url = "https://franime.fr/watch2/?a=published"
        pages["anime/11/0/0/vf/0"] = System.currentTimeMillis() to url
        assertEquals(url, provider.accessUrl("https://franime.fr/anime/11#season=0&episode=0&lang=vf"))
        org.junit.Assert.assertNull(provider.accessUrl("https://franime.fr/anime/11#season=0&episode=1&lang=vf"))
        pages["anime/11/0/0/vf/0"] = 0L to url
        org.junit.Assert.assertNull(provider.accessUrl("https://franime.fr/anime/11#season=0&episode=0&lang=vf"))
        com.streamflixreborn.streamflix.utils.SourceWebSession.rememberValidatedPage(url, "<iframe src='https://video.example/embed'></iframe>")
        assertEquals(url, provider.accessUrl("https://franime.fr/anime/11#season=0&episode=0&lang=vf"))
        pages["anime/11/0/0/vf/0"] = System.currentTimeMillis() to url
        val cachedAt = provider.javaClass.getDeclaredField("cachedAt").apply { isAccessible = true }
        cachedAt.setLong(provider, System.currentTimeMillis())
        provider.clearCatalogueCache(beforeMillis = 1L)
        assertEquals(url, provider.accessUrl("https://franime.fr/anime/11#season=0&episode=0&lang=vf"))
        provider.clearCatalogueCache()
        org.junit.Assert.assertNull(provider.accessUrl("https://franime.fr/anime/11#season=0&episode=0&lang=vf"))
    }
    @Test fun rotatedRequestsKeepOriginPathlessAndDoNotForwardOldCredentials() {
        assertTrue(SourceAddresses.update("Source", "https://old.example/", "https://new.example/"))
        val client = okhttp3.OkHttpClient.Builder().addInterceptor(SourceAddresses.interceptor).addInterceptor { chain ->
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200).message("OK").body("ok".toResponseBody()).build()
        }.build()
        client.newCall(okhttp3.Request.Builder().url("https://old.example/episode/1")
            .header("Origin", "https://old.example").header("Referer", "https://old.example/episode/1")
            .header("Cookie", "session=old").header("Authorization", "old-token").build()).execute().use { response ->
            assertEquals("https://new.example/episode/1", response.request.url.toString())
            assertEquals("https://new.example", response.request.header("Origin"))
            assertEquals("https://new.example/episode/1", response.request.header("Referer"))
            org.junit.Assert.assertNull(response.request.header("Cookie"))
            org.junit.Assert.assertNull(response.request.header("Authorization"))
        }
    }
    @Test fun oustreamerPublicDirectoryReturnsFrenchSources() = kotlinx.coroutines.runBlocking {
        org.junit.Assume.assumeTrue(System.getProperty("tvsama.networkAudit") == "true")
        val entries = SourceDirectory.loadFrench()
        assertTrue("Annuaire français vide", entries.isNotEmpty())
        assertTrue("FRAnime absent de l’annuaire", entries.any { it.slug.filter(Char::isLetterOrDigit).equals("franime", true) })
    }
    @Test fun backupDirectoriesExposeCandidatesWithoutClaimingPlayback() = runBlocking {
        org.junit.Assume.assumeTrue(System.getProperty("tvsama.networkAudit") == "true")
        val report = java.io.File("../verification/DIRECTORY_BACKUPS_NETWORK.md")
        report.writeText("# Annuaires de secours — ${java.time.Instant.now()}\n\nAdresses candidates uniquement ; aucun lecteur ni décodage vérifié.\n\n")
        val failures = mutableListOf<String>()
        for (origin in listOf("AdressesDirect", "Wiki FMHY")) {
            try {
                val records = if (origin == "AdressesDirect") SourceDirectory.fallback("Wiflix") else SourceDirectory.wiki("Wiflix")
                check(records.isNotEmpty()) { "Aucune adresse reconnue" }
                report.appendText("- $origin : ${records.size} adresses Wiflix reconnues ; provenance conservée.\n")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                failures += origin
                report.appendText("- $origin : échec ${e.javaClass.simpleName}.\n")
            }
        }
        assertTrue("Annuaires de secours indisponibles : $failures", failures.isEmpty())
    }
    @Test fun capturedBrowserMediaBecomesReachableOnlyAfterItsBytesAreVerified() = kotlinx.coroutines.runBlocking {
        val server = java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
        val bytes = byteArrayOf(0, 0, 0, 12) + "ftypisom".toByteArray()
        val worker = kotlin.concurrent.thread(isDaemon = true) {
            runCatching { server.accept().use { socket ->
                val reader = socket.getInputStream().bufferedReader()
                while (!reader.readLine().isNullOrBlank()) { }
                socket.getOutputStream().apply {
                    write("HTTP/1.1 200 OK\r\nContent-Type: video/mp4\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    write(bytes); flush()
                }
            } }
        }
        try {
            val candidate = browserMediaCandidate("http://127.0.0.1:${server.localPort}/movie.mp4", emptyMap(),
                "https://source.example/episode", "AnimeKO")!!
            org.junit.Assert.assertFalse(candidate.reachable)
            val verified = validateLiveSource(candidate)
            org.junit.Assert.assertNotNull(verified)
            assertTrue(verified!!.reachable)
            assertEquals("video/mp4", verified.mimeType)
        } finally { server.close(); worker.join(1_000) }
    }
    @Test fun aMasterPlaylistCannotHideAThirtySecondPromo() = kotlinx.coroutines.runBlocking {
        val server = java.net.ServerSocket(0, 4, java.net.InetAddress.getByName("127.0.0.1"))
        val master = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1000\nmedia.m3u8\n".toByteArray()
        val media = "#EXTM3U\n#EXT-X-TARGETDURATION:30\n#EXTINF:30,\nsegment.ts\n#EXT-X-ENDLIST\n".toByteArray()
        val segment = ByteArray(564).apply { this[0] = 0x47; this[188] = 0x47; this[376] = 0x47 }
        val worker = kotlin.concurrent.thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = try { server.accept() } catch (_: java.net.SocketException) { break }
                socket.use {
                    val reader = it.getInputStream().bufferedReader()
                    val path = reader.readLine().orEmpty().split(' ').getOrNull(1)
                    while (!reader.readLine().isNullOrBlank()) { }
                    val bytes = when (path) { "/master.m3u8" -> master; "/media.m3u8" -> media; else -> segment }
                    it.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(bytes); flush()
                    }
                }
            }
        }
        try {
            val source = VideoSource("Uqload", "http://127.0.0.1:${server.localPort}/master.m3u8", "VF", "Auto", "Fixture")
            org.junit.Assert.assertNull(validateLiveSource(source, minimumVodDurationMs = 90_000))
            org.junit.Assert.assertNotNull("Short live windows must remain playable", validateLiveSource(source))
        } finally { server.close(); worker.join(1_000) }
    }
    @Test fun successiveDirectoryAddressChangesKeepOriginalFavouritesUsable() {
        val addresses = com.streamflixreborn.streamflix.utils.SourceAddresses
        assertTrue(addresses.update("AnimeKO", "https://old-source.example/", "https://next-source.example/"))
        assertTrue(addresses.update("AnimeKO", "https://next-source.example/", "https://current-source.example/"))
        assertEquals("https://current-source.example/episode/1?lang=vf#player",
            addresses.rewrite("https://old-source.example/episode/1?lang=vf#player"))
        assertEquals("https://current-source.example/", addresses.current("AnimeKO", "https://old-source.example/"))
    }
    @Test fun frenchAnimeValidationOpensThePublishedFilmOrEpisodePage() {
        val manager = StreamFlixProviderManager.getInstance()
        val movie = Anime("Film", "Film", emptyList(), "film-slug", provider = "FrenchAnime")
        assertEquals("https://french-anime.com/films-vf-vostfr/film-slug.html", manager.browserUrl(movie, null))
        val show = Anime("Série", "Série", emptyList(), "show-slug", provider = "FrenchAnime")
        assertEquals("https://french-anime.com/season-slug.html", manager.browserUrl(show,
            Episode("Épisode", "", emptyList(), "season-slug_3")))
    }
    @Test fun manuallyValidatedPagesStayBoundToTheirExactHttpsAddress() {
        val session = com.streamflixreborn.streamflix.utils.SourceWebSession
        val html = "<iframe src='https://video.example/embed'></iframe>"
        session.rememberValidatedPage("https://source.example/episode/1#player", html)
        assertEquals(html, session.validatedPage("https://source.example/episode/1"))
        org.junit.Assert.assertNull(session.validatedPage("https://source.example/episode/2"))
        org.junit.Assert.assertNull(session.validatedPage("https://other.example/episode/1"))
        session.rememberValidatedPage("http://source.example/insecure", html)
        org.junit.Assert.assertNull(session.validatedPage("http://source.example/insecure"))
        session.rememberValidatedPage("https://source.example/challenge", "<title>Just a moment...</title>")
        org.junit.Assert.assertNull(session.validatedPage("https://source.example/challenge"))
    }
    @Test fun kidrazAddressRequiresARealFilmCatalogue() {
        assertTrue(SourceAddressValidation.compatible("Kidraz", org.jsoup.Jsoup.parse("<div class='film-card'>Film</div>")))
        assertTrue(!SourceAddressValidation.compatible("Kidraz", org.jsoup.Jsoup.parse("<html><body>Accueil</body></html>")))
    }
    private lateinit var application: StreamFlixApp

    @Before fun prepareAddresses() {
        application = StreamFlixApp()
        ReflectionHelpers.callInstanceMethod<Void>(application, "attach",
            ReflectionHelpers.ClassParameter.from(Context::class.java, RuntimeEnvironment.getApplication()))
        StreamFlixApp::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, application)
        com.streamflixreborn.streamflix.utils.UserPreferences.setup(RuntimeEnvironment.getApplication())
        clearAddresses()
    }

    @After fun clearAddresses() {
        application.getSharedPreferences("source_addresses", 0).edit().clear().commit()
    }

    @Test fun migratedRootsKeepSavedFilmPathsAndRepairPersistedAddress() {
        val addresses = com.streamflixreborn.streamflix.utils.SourceAddresses
        addresses.update("WarFlix", "https://warflix.im/film/1423191/", "https://warflix.im/film/1423191/")
        assertEquals("https://warflix.im/", com.streamflixreborn.streamflix.providers.WarFlixProvider.baseUrl)
        assertEquals("https://warflix.im/", addresses.current("WarFlix", "https://warflix.im/"))
        assertEquals("https://warflix.im/film/1423191", addresses.rewrite("https://warflix.im/film/1423191"))
    }

    @Test fun persistedWarflixContentPageDoesNotBecomeTheApiBase() {
        assertTrue(SourceAddresses.update("WarFlix", "https://warflix.im/", "https://warflix.im/film/1423191/"))

        assertEquals("https://warflix.im/", WarFlixProvider.baseUrl)
    }

    @Test fun persistedMyFluneoCataloguePageDoesNotBecomeTheApiBase() {
        assertTrue(SourceAddresses.update("MyFluneo", "https://myfluneo.eu/", "https://myfluneo.eu/catalog/"))

        assertEquals("https://myfluneo.eu/", MyFluneoProvider.baseUrl)
    }

    @Test fun providerWithAnIntentionalBasePathKeepsThatPath() {
        val provider = WordPressStreamingProvider("NestedCatalogue", "https://nested.example/stream/")

        assertEquals("https://nested.example/stream/", provider.baseUrl)
    }

    @Test fun retiredFlemmixAddressRoutesSavedFilmsToCurrentCatalogue() {
        assertTrue(SourceAddresses.update("Wiflix", "https://flemmix.rip/", "https://flemmix.rip/"))
        assertEquals("https://flemmix.land/", WiflixProvider.baseUrl)
        assertEquals("https://flemmix.land/film-en-streaming/example", SourceAddresses.rewrite("https://flemmix.rip/film-en-streaming/example"))
    }
}
