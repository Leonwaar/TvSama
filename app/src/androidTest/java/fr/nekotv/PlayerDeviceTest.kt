package fr.nekotv

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Real Android decoding with generated, known media; does not certify any external provider. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var server: ServerSocket
    private val workers = Executors.newCachedThreadPool()
    private var oldMinutes = 0
    private var oldDeadline = 0L
    @Before fun start() {
        val settings = context.getSharedPreferences("tvsama_settings", Context.MODE_PRIVATE)
        val timer = context.getSharedPreferences("tvsama_sleep_timer", Context.MODE_PRIVATE)
        oldMinutes = settings.getInt("sleep_minutes", 0)
        oldDeadline = timer.getLong("deadline", 0)
        settings.edit().putInt("sleep_minutes", 0).commit()
        timer.edit().remove("deadline").commit()
        server = ServerSocket(0, 10, java.net.InetAddress.getByName("127.0.0.1"))
        workers.execute {
            while (!server.isClosed) {
                val client = try { server.accept() } catch (_: Exception) { break }
                workers.execute { respond(client) }
            }
        }
    }
    @After fun stop() {
        server.close(); workers.shutdownNow()
        context.getSharedPreferences("tvsama_settings", 0).edit().putInt("sleep_minutes", oldMinutes).commit()
        context.getSharedPreferences("tvsama_sleep_timer", 0).edit().putLong("deadline", oldDeadline).commit()
    }
    private fun respond(socket: Socket) = socket.use { client ->
        client.soTimeout = 5000
        val reader = client.getInputStream().bufferedReader()
        val request = reader.readLine() ?: return@use
        val path = request.split(" ").getOrNull(1)?.substringBefore('?')?.removePrefix("/") ?: return@use
        val headers = mutableMapOf<String, String>()
        while (true) { val line = reader.readLine() ?: break; if (line.isEmpty()) break
            headers[line.substringBefore(':').lowercase()] = line.substringAfter(':').trim() }
        val status = when (path) { "403.mp4" -> 403; "404.mp4" -> 404; "500.mp4" -> 500; else -> 200 }
        val bytes = if (path == "html.mp4") "<html>Not a media file</html>".toByteArray()
            else if (status != 200) byteArrayOf()
            else instrumentation.context.assets.open("player-fixtures/$path").use { it.readBytes() }
        val range = Regex("bytes=(\\d+)-(\\d*)").find(headers["range"].orEmpty())
        val start = range?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val end = range?.groupValues?.get(2)?.toIntOrNull()?.coerceAtMost(bytes.lastIndex) ?: bytes.lastIndex
        val partial = status == 200 && range != null && start <= end
        val body = if (partial) bytes.copyOfRange(start, end + 1) else bytes
        val mime = when { path.endsWith("m3u8") -> "application/vnd.apple.mpegurl"; path.endsWith("mpd") -> "application/dash+xml"; path.endsWith("ts") -> "video/mp2t"; else -> "video/mp4" }
        val response = buildString {
            append("HTTP/1.1 ${if (partial) 206 else status} Test\r\nContent-Type: $mime\r\nContent-Length: ${body.size}\r\nConnection: close\r\n")
            if (partial) append("Content-Range: bytes $start-$end/${bytes.size}\r\n")
            append("\r\n")
        }
        client.getOutputStream().apply { write(response.toByteArray()); write(body); flush() }
    }
    private fun source(file: String) = VideoSource("fixture", "http://127.0.0.1:${server.localPort}/$file", "UNKNOWN", "Auto", "Local fixture")
    private fun player(view: View): ExoPlayer? {
        if (view is PlayerView) return view.player as? ExoPlayer
        if (view is ViewGroup) for (index in 0 until view.childCount) player(view.getChildAt(index))?.let { return it }
        return null
    }
    private fun await(message: String, timeout: Long = 20_000, predicate: () -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + timeout
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            if (predicate()) return
            Thread.sleep(25)
        }
        fail(message)
    }
    @Test fun mp4HlsAndDashRenderVideoAndDecodeAudio() {
        for (file in listOf("sample.mp4", "stream.m3u8", "stream.mpd")) {
            ActivityScenario.launch(PlaybackTestActivity::class.java).use { scenario ->
                val frame = AtomicBoolean(false)
                scenario.onActivity { it.source.value = source(file) }
                var attached = false
                await("Player absent: $file") {
                    scenario.onActivity { activity -> player(activity.window.decorView)?.let { p ->
                        if (!attached) { p.addListener(object : Player.Listener {
                            override fun onRenderedFirstFrame() { frame.set(true) }
                        }); attached = true }
                    } }; attached
                }
                var decoded = false
                await("Audio/video not decoded: $file") {
                    scenario.onActivity { activity ->
                        assertEquals(activity.error, "", activity.error)
                        player(activity.window.decorView)?.let { p ->
                            decoded = frame.get() && p.isPlaying && p.currentPosition > 1500 &&
                                p.videoFormat?.width == 320 && p.audioFormat?.sampleMimeType == "audio/mp4a-latm" &&
                                (p.audioDecoderCounters?.renderedOutputBufferCount ?: 0) > 0
                        }
                    }; decoded
                }
            }
        }
    }
    @Test fun invalidMediaAndHttpErrorsReachAnActionableError() {
        for (file in listOf("403.mp4", "404.mp4", "500.mp4", "html.mp4")) {
            ActivityScenario.launch(PlaybackTestActivity::class.java).use { scenario ->
                scenario.onActivity { it.source.value = source(file) }
                var error = ""
                await("No bounded error: $file", 30_000) { scenario.onActivity { error = it.error }; error.isNotBlank() }
                assertTrue(error, error.contains("Essayez un autre serveur"))
            }
        }
    }
    @Test fun expiredTimerPreventsAutoplayAndSourceSwitchFromRestarting() {
        context.getSharedPreferences("tvsama_settings", 0).edit().putInt("sleep_minutes", 60).commit()
        context.getSharedPreferences("tvsama_sleep_timer", 0).edit().putLong("deadline", System.currentTimeMillis() - 1000).commit()
        ActivityScenario.launch(PlaybackTestActivity::class.java).use { scenario ->
            for (file in listOf("sample.mp4", "stream.m3u8")) {
                scenario.onActivity { it.source.value = source(file) }
                var ready = false
                await("Paused media not prepared: $file") {
                    scenario.onActivity { activity -> player(activity.window.decorView)?.let { p ->
                        assertFalse("Expired timer started playback", p.playWhenReady)
                        ready = p.currentMediaItem?.localConfiguration?.uri.toString() == source(file).url &&
                            p.playbackState == Player.STATE_READY
                    } }; ready
                }
            }
        }
    }
    @Test fun timerExpiresDuringPlaybackAndOnlyExplicitResumeRestartsIt() {
        context.getSharedPreferences("tvsama_settings", 0).edit().putInt("sleep_minutes", 60).commit()
        val deadline = System.currentTimeMillis() + 6000
        context.getSharedPreferences("tvsama_sleep_timer", 0).edit().putLong("deadline", deadline).commit()
        ActivityScenario.launch(PlaybackTestActivity::class.java).use { scenario ->
            scenario.onActivity { it.source.value = source("sample.mp4") }
            var playing = false
            await("Media did not start before expiry", 5000) {
                scenario.onActivity { playing = player(it.window.decorView)?.isPlaying == true }; playing
            }
            var paused = false
            await("Timer did not pause playback", 8000) {
                scenario.onActivity { paused = System.currentTimeMillis() >= deadline &&
                    player(it.window.decorView)?.playWhenReady == false }; paused
            }
            Thread.sleep(750)
            scenario.onActivity { assertFalse(player(it.window.decorView)!!.playWhenReady) }
            assertEquals(deadline, context.getSharedPreferences("tvsama_sleep_timer", 0).getLong("deadline", 0))
            assertTrue(RemoteLink.playerCommands.trySend(RemoteCommand(action = "toggle")).isSuccess)
            await("Explicit remote resume failed") {
                scenario.onActivity { playing = player(it.window.decorView)?.isPlaying == true }; playing
            }
            assertTrue(context.getSharedPreferences("tvsama_sleep_timer", 0).getLong("deadline", 0) > deadline)
        }
    }

    @Test fun remotePauseSeekAndSourceReplacementDoNotLeaveOldPlayerRunning() {
        ActivityScenario.launch(PlaybackTestActivity::class.java).use { scenario ->
            scenario.onActivity { it.source.value = source("sample.mp4") }
            var old: ExoPlayer? = null
            await("First player did not start") {
                var playing = false
                scenario.onActivity { old = player(it.window.decorView); playing = old?.isPlaying == true }; playing
            }
            assertTrue(RemoteLink.playerCommands.trySend(RemoteCommand(action = "toggle")).isSuccess)
            await("Remote pause failed") {
                var paused = false
                scenario.onActivity { paused = !old!!.playWhenReady }; paused
            }
            assertTrue(RemoteLink.playerCommands.trySend(RemoteCommand(action = "seek", position = 4000)).isSuccess)
            await("Remote seek failed") {
                var sought = false
                scenario.onActivity { sought = kotlin.math.abs(old!!.currentPosition - 4000) < 250 }; sought
            }
            scenario.onActivity { it.source.value = source("stream.m3u8") }
            await("Replacement player did not start") {
                var replaced = false
                scenario.onActivity { activity -> player(activity.window.decorView)?.let { current ->
                    replaced = current !== old && current.isPlaying
                    if (replaced) assertEquals(Player.STATE_IDLE, old!!.playbackState)
                } }; replaced
            }
        }
    }

}
