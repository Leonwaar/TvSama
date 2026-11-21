package fr.nekotv

import android.app.Application
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class RemoteLinkTest {
    @Test fun resettingPreferencesRevokesTheOldQrAndDisconnectsTheTarget() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val old = RemoteLink.start(context)
        RemoteLink.pair(context, old)
        val oldToken = RemoteLink.token(context)
        val settings = context.getSharedPreferences("tvsama_settings", 0)
        settings.edit().putBoolean("subtitles", false).commit()
        val notified = java.util.concurrent.atomic.AtomicBoolean(false)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "subtitles") notified.set(settings.getBoolean("subtitles", false))
        }
        settings.registerOnSharedPreferenceChangeListener(listener)
        RemoteLink.resetPreferences(context)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        settings.unregisterOnSharedPreferenceChangeListener(listener)
        assertTrue("Reset must notify subtitle observers on Android versions before R", notified.get())
        assertNull(RemoteLink.target.value)
        assertNotEquals(oldToken, RemoteLink.token(context))
        assertTrue(runCatching { RemoteLink.pair(context, old) }.isFailure)
        RemoteLink.pair(context, RemoteLink.start(context))
        assertNotNull(RemoteLink.target.value)
    }
    @Test fun olderPeerDataGetsSafeDefaultsAndExplicitNullsAreRejected() {
        val command = RemoteLink.decodeCommand("""{"action":"play","anime":{"title":"Test","tag":"Série","episodes":[{"title":"E1","description":"","sources":[]}]},"sources":[{"name":"Test","url":"https://media.example/test.mp4","language":"VF","quality":"Auto","provider":"Test"}]}""")
        assertTrue(command.anime!!.aliases.isEmpty())
        assertTrue(command.anime.references.isEmpty())
        assertTrue(command.sources.single().headers.isEmpty())
        assertTrue(command.sources.single().subtitles.isEmpty())
        assertEquals(1, command.anime.episodes.single().number)
        assertTrue(runCatching { RemoteLink.decodeCommand("""{"action":"play","anime":{"title":"Test","episodes":null},"sources":[]}""") }.isFailure)
        assertTrue(runCatching { RemoteLink.decodeSnapshot("""{"playback":null}""") }.isFailure)
        assertTrue(runCatching { RemoteLink.decodeSnapshot("""{"history":[{"anime":null,"duration":100}]}""") }.isFailure)
    }
    @After fun cleanup() {
        RemoteLink.stop()
        RemoteLink.disconnect(RuntimeEnvironment.getApplication())
        while (RemoteLink.commands.tryReceive().isSuccess) { }
    }
    @Test fun pairingDeliversSearchAndMediaWithHeaders() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val payload = RemoteLink.start(context)
        RemoteLink.pair(context, payload)
        assertNotNull(RemoteLink.target.value)
        RemoteLink.send(context, RemoteCommand(action = "search", query = "Film été", category = "Films"))
        val search = withTimeout(2000) { RemoteLink.commands.receive() }
        assertEquals("Film été", search.query)
        assertEquals("Films", search.category)
        val video = VideoSource("Test", "https://example.com/live.m3u8", "VF", "Auto", "Test", mapOf("Referer" to "https://example.com/"))
        RemoteLink.send(context, RemoteCommand(action = "play", anime = Anime("Film", "Film", emptyList()), sources = listOf(video)))
        assertEquals(video, withTimeout(2000) { RemoteLink.commands.receive() }.sources.single())
        RemoteLink.disconnect(context)
        RemoteLink.restore(context)
        assertNull(RemoteLink.target.value)
    }
    @Test fun invalidTokenCannotPairOrEnqueueCommands() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        RemoteLink.disconnect(context)
        val payload = RemoteLink.start(context)
        val invalid = android.net.Uri.parse(payload).buildUpon().clearQuery()
            .appendQueryParameter("host", android.net.Uri.parse(payload).getQueryParameter("host"))
            .appendQueryParameter("token", "000000000000").build().toString()
        assertTrue(runCatching { RemoteLink.pair(context, invalid) }.isFailure)
        assertNull(RemoteLink.target.value)
        assertTrue(RemoteLink.commands.tryReceive().isFailure)
    }
    @Test fun controlsAndSnapshotPreserveTimecodeAndEpisode() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        RemoteLink.pair(context, RemoteLink.start(context))
        RemoteLink.send(context, RemoteCommand(action = "seek", position = 123456))
        assertEquals(123456L, withTimeout(2000) { RemoteLink.commands.receive() }.position)
        RemoteLink.send(context, RemoteCommand(action = "seek_by", position = -15_000))
        assertEquals(-15_000L, withTimeout(2000) { RemoteLink.commands.receive() }.position)
        RemoteLink.send(context, RemoteCommand(action = "key", query = "right"))
        assertEquals("right", withTimeout(2000) { RemoteLink.commands.receive() }.query)
        assertTrue(runCatching { RemoteLink.send(context, RemoteCommand(action = "seek_by", position = 120_001)) }.isFailure)
        RemoteLink.send(context, RemoteCommand(action = "sleep", position = 30))
        withTimeout(2000) { RemoteLink.commands.receive() }.also {
            assertEquals("sleep", it.action)
            assertEquals(30, it.sleepMinutes())
        }
        assertEquals(30, RemoteCommand(action = "sleep").sleepMinutes())
        assertEquals(30, RemoteCommand(action = "sleep", position = 999).sleepMinutes())
        RemoteLink.playback = RemotePlayback(title = "Test", position = 654321, duration = 900000, playing = true, hasNext = true)
        RemoteLink.sync(context)
        assertEquals(654321L, RemoteLink.snapshot.value.playback.position)
        assertTrue(RemoteLink.snapshot.value.playback.hasNext)
        RemoteLink.playback = RemotePlayback()
    }

}
