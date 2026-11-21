package fr.nekotv

import android.app.Application
import android.content.Context
import com.streamflixreborn.streamflix.StreamFlixApp
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Dns
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class MediaSessionSecurityTest {
    @Test fun redirectCannotForwardAnExplicitSessionToAnotherOrigin() {
        val requests = redirectRequests(false)
        assertTrue(requests.first().any { it.startsWith("Cookie:", true) })
        assertFalse(requests.last().any { it.startsWith("Cookie:", true) || it.startsWith("Authorization:", true) })
    }
    @Test fun destinationKeepsItsOwnDomainScopedCookieAfterARedirect() {
        val requests = redirectRequests(true)
        val cookie = requests.last().firstOrNull { it.startsWith("Cookie:", true) }.orEmpty()
        assertTrue(cookie.contains("destination=own"))
        assertFalse(cookie.contains("session=fake"))
        assertFalse(requests.last().any { it.startsWith("Authorization:", true) })
    }
    private fun redirectRequests(destinationCookie: Boolean): List<List<String>> {
        val app = StreamFlixApp()
        ReflectionHelpers.callInstanceMethod<Void>(app, "attach",
            ReflectionHelpers.ClassParameter.from(Context::class.java, RuntimeEnvironment.getApplication()))
        StreamFlixApp::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, app)
        com.streamflixreborn.streamflix.utils.UserPreferences.setup(app)
        app.getSharedPreferences("source_addresses", 0).edit().clear().commit()
        android.webkit.CookieManager.getInstance().removeAllCookies(null)
        val worker = Executors.newSingleThreadExecutor()
        return ServerSocket(0).use { server ->
            val destination = "redirect-${System.nanoTime()}.example"
            if (destinationCookie) com.streamflixreborn.streamflix.utils.NetworkClient.cookieJar.saveFromResponse(
                "http://$destination:${server.localPort}/media".toHttpUrl(), listOf(okhttp3.Cookie.Builder()
                    .name("destination").value("own").hostOnlyDomain(destination).build()))
            val received = worker.submit<List<List<String>>> {
                (0..1).map { index -> server.accept().use { socket ->
                    socket.soTimeout = 3000
                    val reader = socket.getInputStream().bufferedReader()
                    val lines = generateSequence { reader.readLine()?.takeUnless(String::isEmpty) }.toList()
                    val reply = if (index == 0) "HTTP/1.1 302 Found\r\nLocation: http://$destination:${server.localPort}/media\r\n"
                        else "HTTP/1.1 200 OK\r\n"
                    socket.getOutputStream().write((reply + "Content-Length: 0\r\nConnection: close\r\n\r\n").toByteArray())
                    lines
                } }
            }
            try {
                val client = MediaNetwork.client.newBuilder().dns(object : Dns {
                    override fun lookup(hostname: String) = listOf(java.net.InetAddress.getByName("127.0.0.1"))
                }).build()
                client.newCall(Request.Builder().url("http://127.0.0.1:${server.localPort}/start")
                    .header("Cookie", "session=fake").header("Authorization", "Bearer fake").build()).execute().close()
                received.get(5, TimeUnit.SECONDS)
            } finally { worker.shutdownNow() }
        }
    }
}
