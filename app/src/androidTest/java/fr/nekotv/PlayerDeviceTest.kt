package fr.nekotv

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.KeyEvent
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
    private fun send(command: RemoteCommand) {
        instrumentation.runOnMainSync { assertTrue(RemoteLink.playerCommands.trySend(command).isSuccess) }
    }
    private fun player(view: View): ExoPlayer? {
        if (view is PlayerView) return view.player as? ExoPlayer
        if (view is ViewGroup) for (index in 0 until view.childCount) player(view.getChildAt(index))?.let { return it }
        return null
    }
    private fun playerView(view: View): PlayerView? {
        if (view is PlayerView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount)
            playerView(view.getChildAt(index))?.let { return it }
        return null
    }
    private fun confirm(view: PlayerView, key: Int, repeat: Boolean = false) {
        val now = android.os.SystemClock.uptimeMillis()
        assertTrue(view.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, key, 0)))
        if (repeat) assertTrue(view.dispatchKeyEvent(KeyEvent(now, now + 100, KeyEvent.ACTION_DOWN, key, 1)))
        assertTrue(view.dispatchKeyEvent(KeyEvent(now, now + 150, KeyEvent.ACTION_UP, key, 0)))
    }

    @Test fun confirmRevealsControlsThenTogglesTimelineAndNativePauseExactlyOnce() {
        ActivityScenario.launch(PlaybackTestActivity::class.java).use { scenario ->
            scenario.onActivity { it.source.value = source("sample.mp4") }
            await("Player did not start") {
                var ready = false
                scenario.onActivity { ready = player(it.window.decorView)?.isPlaying == true }; ready
            }
            for (key in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                scenario.onActivity {
                    val view = playerView(it.window.decorView)!!
                    view.player!!.repeatMode = Player.REPEAT_MODE_ONE
                    view.player!!.play()
                    view.hideController()
                }
                await("Controller did not hide") {
                    var hidden = false
                    scenario.onActivity { hidden = !playerView(it.window.decorView)!!.isControllerFullyVisible }; hidden
                }
                scenario.onActivity {
                    val view = playerView(it.window.decorView)!!
                    confirm(view, key, repeat = true)
                    assertTrue("Revealing controls paused playback", view.player!!.playWhenReady)
                }
                await("Controller did not open") {
                    var visible = false
                    scenario.onActivity { visible = playerView(it.window.decorView)!!.isControllerFullyVisible }; visible
                }
                scenario.onActivity {
                    val view = playerView(it.window.decorView)!!
                    val bar = view.findViewById<View>(androidx.media3.ui.R.id.exo_progress)
                    bar.isFocusableInTouchMode = true
                    assertTrue(bar.requestFocus())
                    confirm(view, key, repeat = true)
                    assertFalse("Timeline OK must pause once", view.player!!.playWhenReady)
                    confirm(view, key)
                    assertTrue("Timeline OK must resume once", view.player!!.playWhenReady)
                    // A directional seek must be committed before OK toggles playback.
                    view.player!!.pause()
                    view.player!!.seekTo(1000)
                    var scrubTarget = -1L
                    val scrubListener = object : androidx.media3.ui.TimeBar.OnScrubListener {
                        override fun onScrubStart(timeBar: androidx.media3.ui.TimeBar, position: Long) { scrubTarget = position }
                        override fun onScrubMove(timeBar: androidx.media3.ui.TimeBar, position: Long) { scrubTarget = position }
                        override fun onScrubStop(timeBar: androidx.media3.ui.TimeBar, position: Long, canceled: Boolean) {
                            assertFalse("OK canceled the directional seek", canceled)
                            scrubTarget = position
                        }
                    }
                    (bar as androidx.media3.ui.DefaultTimeBar).addListener(scrubListener)
                    bar.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
                    bar.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT))
                    confirm(view, key)
                    assertTrue(view.player!!.playWhenReady)
                    assertTrue("No directional seek occurred", scrubTarget >= 0)
                    assertEquals("Pending directional seek was lost", scrubTarget, view.player!!.currentPosition)
                    bar.removeListener(scrubListener)
                    val pause = view.findViewById<View>(androidx.media3.ui.R.id.exo_play_pause)
                    pause.isFocusableInTouchMode = true
                    assertTrue(pause.requestFocus())
                    confirm(view, key)
                    assertFalse("Pause button OK must pause once", view.player!!.playWhenReady)
                    confirm(view, key)
                    assertTrue("Pause button OK must resume once", view.player!!.playWhenReady)
                }
            }
        }
    }

    @Test fun sleepIconFollowsControllerVisibilityAndStillOpensTimerMenu() {
        ActivityScenario.launch(PlaybackTestActivity::class.java).use { scenario ->
            scenario.onActivity { it.source.value = source("sample.mp4") }
            await("Looping player did not start") {
                var playing = false
                scenario.onActivity { player(it.window.decorView)?.let { p ->
                    p.repeatMode = Player.REPEAT_MODE_ONE
                    playing = p.isPlaying
                } }; playing
            }
            await("Sleep icon missing") { accessible("Minuterie de veille") != null }
            assertTrue(accessible("Minuterie de veille")!!.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
            await("Timer menu missing") { accessible("Désactiver", text = true) != null }
            assertTrue("Timer menu item could not be activated", clickAccessible(accessible("Désactiver", text = true)!!))
            val started = android.os.SystemClock.elapsedRealtime()
            // Check the wall-clock deadline, without a virtual Compose clock changing player coroutines.
            Thread.sleep(6000)
            assertNotNull("Controls hid before eight seconds", accessible("Minuterie de veille"))
            await("Sleep icon persisted after eight seconds", 5000) { accessible("Minuterie de veille") == null }
            assertTrue(android.os.SystemClock.elapsedRealtime() - started >= 7500)
            scenario.onActivity {
                val view = playerView(it.window.decorView)!!
                assertTrue(view.player!!.playWhenReady)
                confirm(view, KeyEvent.KEYCODE_DPAD_CENTER)
            }
            await("OK did not restore the sleep icon") { accessible("Minuterie de veille") != null }
        }
    }
    private fun accessible(label: String, text: Boolean = false): android.view.accessibility.AccessibilityNodeInfo? {
        fun find(node: android.view.accessibility.AccessibilityNodeInfo): android.view.accessibility.AccessibilityNodeInfo? {
            if ((if (text) node.text else node.contentDescription)?.toString() == label) return node
            for (index in 0 until node.childCount) node.getChild(index)?.let { find(it)?.let { found -> return found } }
            return null
        }
        return instrumentation.uiAutomation.rootInActiveWindow?.let(::find)
    }
    private fun clickAccessible(node: android.view.accessibility.AccessibilityNodeInfo): Boolean {
        var target: android.view.accessibility.AccessibilityNodeInfo? = node
        while (target != null) {
            if (target.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) return true
            target = target.parent
        }
        return false
    }
    @Test fun autoNextReplacesTheStreamAtThirtySecondsRemainingExactlyOnce() {
        ActivityScenario.launch(PlaybackTestActivity::class.java).use { scenario ->
            scenario.onActivity { it.source.value = source("sample-long.mp4"); it.nextSource.value = source("sample.mp4") }
            var old: ExoPlayer? = null
            await("Long episode did not start") {
                var ready = false
                scenario.onActivity { old = player(it.window.decorView); ready = old?.isPlaying == true }; ready
            }
            scenario.onActivity {
                assertTrue(old!!.duration >= 45_000)
                old!!.seekTo(old!!.duration - 31_000)
            }
            Thread.sleep(300)
            scenario.onActivity { assertEquals("Advanced before the threshold", 0, it.advances) }
            scenario.onActivity { old!!.seekTo(old!!.duration - 30_000) }
            await("Next episode did not replace the old stream") {
                var advanced = false
                scenario.onActivity { activity ->
                    val next = player(activity.window.decorView)
                    advanced = next !== old && next?.isPlaying == true
                    if (advanced) {
                        assertEquals(1, activity.advances)
                        assertFalse("Ended callback duplicated automatic advance", activity.ended)
                        assertEquals(Player.STATE_IDLE, old!!.playbackState)
                    }
                }; advanced
            }
            Thread.sleep(500)
            scenario.onActivity { assertEquals(1, it.advances) }
        }
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
    // The player already restarts the saved duration on each new source. These
    // assertions follow that contract; begin()/expiry are covered with a fake clock in JVM tests.
    @Test fun newPlaybackAndSourceReplacementStartFreshConfiguredSleepCountdowns() {
        context.getSharedPreferences("tvsama_settings", 0).edit().putInt("sleep_minutes", 60).commit()
        context.getSharedPreferences("tvsama_sleep_timer", 0).edit().putLong("deadline", System.currentTimeMillis() - 1000).commit()
        ActivityScenario.launch(PlaybackTestActivity::class.java).use { scenario ->
            for (file in listOf("sample.mp4", "stream.m3u8")) {
                val startedAt = System.currentTimeMillis()
                scenario.onActivity { it.source.value = source(file) }
                var ready = false
                await("Fresh media not prepared: $file") {
                    scenario.onActivity { activity -> player(activity.window.decorView)?.let { p ->
                        assertTrue("New playback must start a fresh countdown", p.playWhenReady)
                        ready = p.currentMediaItem?.localConfiguration?.uri.toString() == source(file).url &&
                            p.playbackState == Player.STATE_READY
                    } }; ready
                }
                assertTrue(context.getSharedPreferences("tvsama_sleep_timer", 0).getLong("deadline", 0) >=
                    startedAt + 60 * 60_000L)
            }
        }
    }
    @Test fun explicitRemoteResumeRefreshesAnExpiredStoredDeadline() {
        context.getSharedPreferences("tvsama_settings", 0).edit().putInt("sleep_minutes", 60).commit()
        ActivityScenario.launch(PlaybackTestActivity::class.java).use { scenario ->
            scenario.onActivity { it.source.value = source("sample.mp4") }
            var playing = false
            await("Media did not start") {
                scenario.onActivity { playing = player(it.window.decorView)?.isPlaying == true }; playing
            }
            send(RemoteCommand(action = "toggle"))
            var paused = false
            await("Remote pause failed") {
                scenario.onActivity { paused = player(it.window.decorView)?.playWhenReady == false }; paused
            }
            // Expire the persisted deadline to exercise the real remote-resume branch
            // without waiting the minimum selectable hour. This does not simulate automatic expiry.
            val deadline = System.currentTimeMillis() - 1000
            context.getSharedPreferences("tvsama_sleep_timer", 0).edit().putLong("deadline", deadline).commit()
            assertEquals(deadline, context.getSharedPreferences("tvsama_sleep_timer", 0).getLong("deadline", 0))
            send(RemoteCommand(action = "toggle"))
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
            send(RemoteCommand(action = "toggle"))
            await("Remote pause failed") {
                var paused = false
                scenario.onActivity { paused = !old!!.playWhenReady }; paused
            }
            send(RemoteCommand(action = "seek", position = 4000))
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
