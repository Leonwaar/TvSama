package fr.nekotv

import android.app.Application
import java.io.File
import java.time.Instant
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AppUpdatesTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("tvsama_updates", 0)
    private val date get() = Instant.ofEpochMilli(BuildConfig.BUILT_AT_MILLIS).plusSeconds(3600).toString()
    @Before fun reset() { prefs.edit().clear().commit() }
    private fun release(digest: String = "") = AppRelease("v0.1", "https://github.com/Leonwaar/TvSama/releases/download/v0.1/app.apk", 3, digest, date)
    @Test fun lowerTagWithLaterPublicationIsOfferedAndCanceledInstallRemainsOffered() {
        assertTrue(AppUpdates.shouldOffer(context, release()))
        prefs.edit().putString("pending_hash", "sha256:other").putString("pending_publication", date).commit()
        assertTrue(AppUpdates.shouldOffer(context, release()))
        assertFalse(AppUpdates.shouldOffer(context, release().copy(publishedAt = Instant.ofEpochMilli(BuildConfig.BUILT_AT_MILLIS).toString())))
    }
    @Test fun sameInstalledBinaryIsNotOfferedAndPendingInstallIsAcknowledged() {
        val file = File(context.cacheDir, "installed.apk").apply { writeText("apk") }
        context.applicationInfo.sourceDir = file.path
        val digest = "sha256:" + java.security.MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        assertFalse(AppUpdates.shouldOffer(context, release(digest)))
        assertEquals(Instant.parse(date).toEpochMilli(), prefs.getLong("installed_publication", 0))
        prefs.edit().clear().putString("pending_hash", digest).putString("pending_publication", date).commit()
        assertFalse(AppUpdates.shouldOffer(context, release()))
        assertFalse(prefs.contains("pending_hash"))
        file.delete()
    }
    @Test fun parserUsesPublicationAndUniversalAssetAndSkipsUnpublishedReleases() {
        val json = JSONObject("""{"tag_name":"v0.1","published_at":"2026-10-15T12:00:00Z","assets":[{"name":"app-arm.apk","browser_download_url":"arm","size":2},{"name":"app-universal.apk","browser_download_url":"universal","size":3}]}""")
        assertEquals("universal", AppUpdates.parseRelease(json)?.assetUrl)
        assertEquals("2026-10-15T12:00:00Z", AppUpdates.parseRelease(json)?.publishedAt)
        assertNull(AppUpdates.parseRelease(JSONObject(json.toString()).put("prerelease", true)))
        assertNull(AppUpdates.parseRelease(JSONObject(json.toString()).put("draft", true)))
        assertNull(AppUpdates.parseRelease(JSONObject(json.toString()).put("published_at", "invalid")))
    }
    @Test fun ambiguousOldApksAndMalformedAssetsDoNotHideAValidRelease() {
        val old = JSONObject("""{"tag_name":"old","published_at":"2026-10-14T12:00:00Z","assets":[{"name":"arm.apk"},{"name":"x86.apk"}]}""")
        assertNull(AppUpdates.parseRelease(old))
        assertNull(AppUpdates.parseRelease(JSONObject(old.toString()).put("assets", org.json.JSONArray("[null]"))))
        val valid = JSONObject("""{"tag_name":"new","published_at":"2026-10-15T12:00:00Z","assets":[{"name":"universal.apk","browser_download_url":"https://github.com/Leonwaar/TvSama/releases/download/new/app.apk","size":3}]}""")
        assertEquals(listOf("new"), listOf(valid, old).mapNotNull(AppUpdates::parseRelease).map { it.version })
    }
}
