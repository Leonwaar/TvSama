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
}
