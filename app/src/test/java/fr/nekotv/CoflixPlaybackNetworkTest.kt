package fr.nekotv

import android.app.Application
import android.content.Context
import com.streamflixreborn.streamflix.StreamFlixApp
import com.streamflixreborn.streamflix.utils.UserPreferences
import kotlinx.coroutines.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class CoflixPlaybackNetworkTest {
    @Test(timeout = 180_000) fun spiderManHasAFullLengthAlternativeWithoutAcceptingShortPlaylists() = runBlocking {
        assumeTrue(System.getProperty("tvsama.networkAudit") == "true")
        val app = StreamFlixApp()
        ReflectionHelpers.callInstanceMethod<Void>(app, "attach",
            ReflectionHelpers.ClassParameter.from(Context::class.java, RuntimeEnvironment.getApplication()))
        StreamFlixApp::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, app)
        UserPreferences.setup(app)
        val manager = StreamFlixProviderManager.getInstance()
        val page = "https://coflix.ac/film/spider-man-brand-new-day/"
        val details = Anime("Spider-Man : Brand New Day", "Film", listOf(Episode("Film", "", emptyList(), page)),
            page, provider = "Coflix", references = listOf(MediaReference("Coflix", page, "Film")))
        val report = File("../verification/COFLIX_VIDEO_NETWORK.md")
        report.parentFile?.mkdirs()
        report.writeText("# Coflix : contrôle vidéo — ${java.time.Instant.now()}\n\nTitre : Spider-Man : Brand New Day.\n\n")
        val sources = stage { manager.resolveSources(details, language = "VF", refresh = true) }
        report.appendText("${sources.size} flux retournés par le gestionnaire natif.\n\n")
        org.robolectric.shadows.ShadowLog.getLogsForTag("DnsResolver").filter {
            it.msg.contains("Failed to resolve") || it.msg.contains("System DNS resolved")
        }.takeLast(10).forEach { entry ->
            report.appendText("DNS : ${entry.msg.replace(Regex("https?://\\S+"), "[résolveur]").take(200)}\n")
        }
        var fullLength = 0
        for (source in sources) {
            try {
                val duration = vodDuration(source)
                val reachable = validateLiveSource(source) != null
                report.appendText("- ${source.name} : ${source.mimeType} ; durée média ${duration?.div(1000)} s ; échantillon accessible : $reachable.\n")
                assertTrue("Un extrait court a passé la recherche : ${source.name}", duration == null || duration >= 90_000)
                if (duration != null && duration >= 120_000 && reachable) {
                    val frame = decodeFrame(source)
                    report.appendText("  Image décodée à 120 s : ${frame.length()} octets.\n")
                    fullLength++
                }
            } catch (e: Exception) {
                report.appendText("- ${source.name} : vérification incomplète (${e.javaClass.simpleName}).\n")
            }
        }
        if (fullLength == 0) {
            try {
                val provider = com.streamflixreborn.streamflix.providers.CoflixProvider
                val type = com.streamflixreborn.streamflix.models.Video.Type.Movie(page, details.title, "2026", "", null)
                val servers = stage { provider.getServers(page, type) }
                withTimeoutOrNull(90_000) {
                    for (server in servers.filter { it.name.contains("VF", true) && sources.none { source -> source.serverId == it.id } }) {
                        try {
                            val video = stage { withTimeout(24_000) { provider.getVideo(server) } }
                            val candidate = VideoSource(server.name, video.source, "VF", "Auto", provider.name, video.headers.orEmpty())
                            val duration = vodDuration(candidate)
                            val reachable = validateLiveSource(candidate) != null
                            report.appendText("\nDiagnostic isolé ${server.name} : durée ${duration?.div(1000)} s ; échantillon accessible : $reachable.\n")
                            if (duration != null && duration >= 90_000 && reachable) break
                        } catch (e: Exception) {
                            if (e is CancellationException && e !is TimeoutCancellationException) throw e
                            report.appendText("\nDiagnostic isolé ${server.name} : ${e.javaClass.simpleName}.\n")
                        }
                    }
                }
            } catch (e: Exception) {
                report.appendText("\nDiagnostic des lecteurs incomplet : ${e.javaClass.simpleName}.\n")
            }
        }
        report.appendText("\n$fullLength flux de durée supérieure à 90 s avec échantillon accessible. Le contenu des images et le décodage Android restent à vérifier. Le contrôle MP4 utilise ffprobe sur la machine de test.\n")
        assertTrue("Aucun repli de durée complète confirmé ; voir verification/COFLIX_VIDEO_NETWORK.md", fullLength > 0)
    }

    private suspend fun <T> stage(block: suspend () -> T): T = coroutineScope {
        val task = async(Dispatchers.IO) { withTimeout(60_000) { block() } }
        while (!task.isCompleted) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            delay(10)
        }
        task.await()
    }

    private fun vodDuration(source: VideoSource): Long? {
        var address = source.url
        repeat(4) {
            val request = Request.Builder().url(address).apply {
                source.headers.forEach { (key, value) ->
                    if (address.toHttpUrl().host == source.url.toHttpUrl().host ||
                        !key.equals("Cookie", true) && !key.equals("Authorization", true)) header(key, value)
                }
            }.header("Range", "bytes=0-2097151").build()
            val (resolved, bytes) = MediaNetwork.probeClient.newCall(request).execute().use { response ->
                check(response.isSuccessful)
                response.request.url.toString() to (response.body ?: error("Flux vide")).byteStream().use { it.readNBytes(2097152) }
            }
            if (mediaMimeType(bytes) != "application/x-mpegURL") return fileDuration(source)
            val playlist = bytes.toString(Charsets.UTF_8)
            val next = hlsProbe(playlist, resolved).variant
            if (next == null) return hlsVodDurationMs(playlist)
            address = next
        }
        return null
    }

    private fun fileDuration(source: VideoSource): Long? {
        val headers = source.headers.entries.joinToString("") { "${it.key}: ${it.value}\r\n" }
        val errors = java.nio.file.Files.createTempFile("tvsama-ffprobe-", ".log").toFile().apply { deleteOnExit() }
        val process = ProcessBuilder("ffprobe", "-v", "error", "-rw_timeout", "5000000", "-headers", headers,
            "-show_entries", "format=duration:stream=codec_type,duration", "-of", "json", source.url)
            .redirectError(errors).start()
        try {
            check(process.waitFor(20, TimeUnit.SECONDS)) { "Délai ffprobe dépassé" }
            check(process.exitValue() == 0) { "Métadonnées MP4 inaccessibles" }
            val json = com.google.gson.JsonParser.parseString(process.inputStream.bufferedReader().readText()).asJsonObject
            val durations = listOfNotNull(json.getAsJsonObject("format")?.get("duration")?.asString?.toDoubleOrNull()) +
                json.getAsJsonArray("streams")?.mapNotNull { it.asJsonObject.get("duration")?.asString?.toDoubleOrNull() }.orEmpty()
            println("ffprobe ${source.name} : $json")
            return durations.filter { it.isFinite() && it > 0 }.maxOrNull()?.let { (it * 1000).toLong() }
        } finally { if (process.isAlive) process.destroyForcibly(); errors.delete() }
    }

    private fun decodeFrame(source: VideoSource): File {
        val image = java.nio.file.Files.createTempFile("tvsama-coflix-frame-", ".png").toFile()
        val errors = java.nio.file.Files.createTempFile("tvsama-ffmpeg-", ".log").toFile().apply { deleteOnExit() }
        val headers = source.headers.entries.joinToString("") { "${it.key}: ${it.value}\r\n" }
        val process = ProcessBuilder("ffmpeg", "-v", "error", "-rw_timeout", "5000000", "-headers", headers,
            "-ss", "120", "-i", source.url, "-threads", "1", "-frames:v", "1", "-an", "-y", image.path)
            .redirectError(errors).start()
        try {
            check(process.waitFor(30, TimeUnit.SECONDS)) { "Délai du décodage dépassé" }
            check(process.exitValue() == 0 && image.length() > 0) { "Image inaccessible après deux minutes" }
            return image
        } finally { if (process.isAlive) process.destroyForcibly(); errors.delete() }
    }
}
