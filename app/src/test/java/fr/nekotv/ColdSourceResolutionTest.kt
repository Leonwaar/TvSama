package fr.nekotv

import android.app.Application
import android.content.Context
import com.streamflixreborn.streamflix.StreamFlixApp
import com.streamflixreborn.streamflix.models.Season
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.providers.Provider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.net.ServerSocket
import java.util.concurrent.Executors

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ColdSourceResolutionTest {
    @Test fun nineColdSeriesProvidersCanLoadTheirSeasonsWithoutBlockingEachOther() = runBlocking {
        val app = StreamFlixApp()
        ReflectionHelpers.callInstanceMethod<Void>(app, "attach",
            ReflectionHelpers.ClassParameter.from(Context::class.java, RuntimeEnvironment.getApplication()))
        StreamFlixApp::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, app)
        com.streamflixreborn.streamflix.utils.UserPreferences.setup(app)
        val anotherProviderStarted = java.util.concurrent.CountDownLatch(1)
        val blockedByOneProvider = java.util.concurrent.atomic.AtomicBoolean(false)
        val workers = Executors.newFixedThreadPool(4)
        ServerSocket(0).use { server ->
            workers.execute {
                while (!server.isClosed) {
                    val socket = try { server.accept() } catch (_: Exception) { break }
                    workers.execute { socket.use {
                        it.soTimeout = 2000
                        val reader = it.getInputStream().bufferedReader()
                        val request = reader.readLine().orEmpty()
                        while (!reader.readLine().isNullOrEmpty()) { }
                        val body = if (request.contains(".m3u8"))
                            "#EXTM3U\n#EXTINF:60,\nsegment.ts\n#EXTINF:60,\nsegment.ts\n#EXT-X-ENDLIST\n".toByteArray()
                        else ByteArray(188 * 3).apply { this[0] = 0x47; this[188] = 0x47; this[376] = 0x47 }
                        it.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray() + body)
                    } }
                }
            }
            val providers = (1..9).map { index ->
                java.lang.reflect.Proxy.newProxyInstance(Provider::class.java.classLoader, arrayOf(Provider::class.java)) { _, method, args ->
                    when (method.name) {
                        "getName" -> "ColdSource$index"
                        "getBaseUrl" -> "http://127.0.0.1:${server.localPort}/"
                        "getLanguage" -> "fr"
                        "getTvShow" -> TvShow(args!![0] as String, "Programme", seasons = listOf(Season("season-$index", 1, "Saison 1")))
                        "getEpisodesBySeason" -> if (index == 2) listOf(
                            com.streamflixreborn.streamflix.models.Episode("episode-2-vostfr", 1, title = "Épisode VOSTFR"),
                            com.streamflixreborn.streamflix.models.Episode("episode-2-vf", 1, title = "Épisode VF")
                        ) else listOf(com.streamflixreborn.streamflix.models.Episode("episode-$index", 1, title = "Épisode VF"))
                        "getServers" -> (1..if (index == 1) 20 else 1).map {
                            val id = args!![0] as String
                            Video.Server(if (index == 2) id else "reader-$index-$it",
                                if (id.contains("vostfr")) "Lecteur VOSTFR" else "Lecteur VF")
                        }
                        "getVideo" -> {
                            if (index == 1) {
                                if (!anotherProviderStarted.await(1, java.util.concurrent.TimeUnit.SECONDS)) blockedByOneProvider.set(true)
                            } else anotherProviderStarted.countDown()
                            val path = if (index == 2) (args!![0] as Video.Server).id else "$index"
                            Video("http://127.0.0.1:${server.localPort}/$path.m3u8")
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
                val episode = Episode("Épisode VF", "", emptyList(), "episode-1")
                val anime = Anime("Programme", "Série", listOf(episode), "cold-series", provider = providers.first().name,
                    references = providers.map { MediaReference(it.name, "series-${it.name}", "Série") })
                val sources = withTimeout(5000) { manager.resolveSources(anime, episode, "VF", refresh = true) }
                assertEquals(providers.map { it.name }.toSet(), sources.map { it.provider }.toSet())
                assertEquals(setOf("VF"), sources.filter { it.provider == "ColdSource2" }.map { it.language }.toSet())
                org.junit.Assert.assertFalse("One site's mirrors must leave room for other providers", blockedByOneProvider.get())
            } finally { field.set(manager, original); server.close(); workers.shutdownNow() }
        }
    }
}
