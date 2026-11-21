package fr.nekotv

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONArray
import java.time.Instant
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class AppRelease(val version: String, val assetUrl: String, val size: Long, val digest: String, val publishedAt: String)
object AppUpdates {
    private fun connection(url: String, binary: Boolean = false): HttpURLConnection {
        val target = URL(url)
        check(target.protocol == "https") { "Adresse de mise à jour non sécurisée" }
        return (target.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 30_000; instanceFollowRedirects = false
            setRequestProperty("Accept", if (binary) "application/octet-stream" else "application/vnd.github+json")
            setRequestProperty("User-Agent", "TvSama-Updater")
        }
    }
    fun newer(remote: String, local: String): Boolean {
        return runCatching { Instant.parse(remote).isAfter(Instant.parse(local)) }.getOrDefault(false)
    }
    internal fun parseRelease(json: JSONObject): AppRelease? {
        return try {
            if (json.optBoolean("draft") || json.optBoolean("prerelease")) return null
            val published = runCatching { Instant.parse(json.getString("published_at")) }.getOrNull() ?: return null
            val array = json.optJSONArray("assets") ?: return null
            val apks = (0 until array.length()).mapNotNull { array.optJSONObject(it) }.filter { it.optString("name").endsWith(".apk", true) }
            if (apks.isEmpty()) return null
            val asset = apks.firstOrNull { it.optString("name").contains("universal", true) } ?: apks.singleOrNull()
                ?: return null
            AppRelease(json.getString("tag_name"), asset.getString("browser_download_url"),
                asset.getLong("size"), asset.optString("digest"), published.toString())
        } catch (_: org.json.JSONException) { null }
    }
    private fun sha256(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        return "sha256:" + digest.digest().joinToString("") { "%02x".format(it) }
    }
    internal fun shouldOffer(context: Context, release: AppRelease): Boolean {
        val prefs = context.getSharedPreferences("tvsama_updates", Context.MODE_PRIVATE)
        val installedHash by lazy { File(context.applicationInfo.sourceDir).takeIf { it.isFile }?.let(::sha256) }
        val pending = prefs.getString("pending_hash", null)
        if (pending != null && pending == installedHash) {
            val publication = runCatching { Instant.parse(prefs.getString("pending_publication", "")).toEpochMilli() }.getOrNull()
            if (publication != null) prefs.edit().putLong("installed_publication", publication)
                .remove("pending_hash").remove("pending_publication").apply()
        }
        val reference = maxOf(BuildConfig.BUILT_AT_MILLIS, prefs.getLong("installed_publication", 0))
        if (!newer(release.publishedAt, Instant.ofEpochMilli(reference).toString())) return false
        if (release.digest.startsWith("sha256:") && release.digest == installedHash) {
            prefs.edit().putLong("installed_publication", Instant.parse(release.publishedAt).toEpochMilli()).apply()
            return false
        }
        return true
    }
    suspend fun latest(context: Context): AppRelease? = withContext(Dispatchers.IO) {
        check(BuildConfig.UPDATE_REPOSITORY.isNotBlank()) { "Dépôt GitHub absent de la configuration de compilation" }
        val releases = mutableListOf<AppRelease>()
        var page = 1
        do {
            val conn = connection("https://api.github.com/repos/${BuildConfig.UPDATE_REPOSITORY}/releases?per_page=100&page=$page")
            val items = try {
                check(conn.responseCode == 200) { "Vérification GitHub : HTTP ${conn.responseCode}" }
                JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
            } finally { conn.disconnect() }
            for (i in 0 until items.length()) items.optJSONObject(i)?.let(::parseRelease)?.let(releases::add)
            page++
        } while (items.length() == 100)
        releases.maxByOrNull { Instant.parse(it.publishedAt) }?.takeIf { shouldOffer(context, it) }
    }
    suspend fun download(context: Context, release: AppRelease): File = withContext(Dispatchers.IO) {
        check(release.assetUrl.startsWith("https://github.com/${BuildConfig.UPDATE_REPOSITORY}/releases/download/"))
        check(release.size in 1..500_000_000) { "Taille APK invalide" }
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val file = File(directory, "update.apk")
        var url = release.assetUrl
        var downloaded = false
        repeat(6) {
            if (!downloaded) {
                val conn = connection(url, true)
                try {
                    if (conn.responseCode in listOf(301, 302, 303, 307, 308)) {
                        url = URL(URL(url), conn.getHeaderField("Location") ?: error("Redirection invalide")).toString()
                    } else {
                        check(conn.responseCode == 200) { "Téléchargement : HTTP ${conn.responseCode}" }
                        conn.inputStream.use { input -> file.outputStream().use { output ->
                            val buffer = ByteArray(65536); var total = 0L
                            while (true) { val n = input.read(buffer); if (n < 0) break; total += n; check(total <= release.size) { "APK trop volumineux" }; output.write(buffer, 0, n) }
                        } }
                        check(file.length() == release.size) { "Téléchargement incomplet" }
                        downloaded = true
                    }
                } finally { conn.disconnect() }
            }
        }
        check(downloaded) { "Trop de redirections" }
        val fingerprint = sha256(file)
        if (release.digest.startsWith("sha256:")) check(fingerprint == release.digest) { "Empreinte APK incorrecte" }
        @Suppress("DEPRECATION")
        val flags = PackageManager.GET_SIGNATURES
        @Suppress("DEPRECATION")
        val archive = context.packageManager.getPackageArchiveInfo(file.path, flags) ?: error("APK invalide")
        check(archive.packageName == context.packageName) { "Cet APK ne correspond pas à TvSama" }
        @Suppress("DEPRECATION")
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        @Suppress("DEPRECATION")
        check(archive.signatures?.toSet() == installed.signatures?.toSet() && !archive.signatures.isNullOrEmpty()) { "Signature incompatible avec la version installée" }
        context.getSharedPreferences("tvsama_updates", Context.MODE_PRIVATE).edit()
            .putString("pending_hash", fingerprint).putString("pending_publication", release.publishedAt).apply()
        file
    }
    fun install(context: Context, file: File) {
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
}

@Composable
fun UpdatePrompt(manual: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var release by remember { mutableStateOf<AppRelease?>(null) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var apk by remember { mutableStateOf<File?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var checking by remember { mutableStateOf(true) }
    LaunchedEffect(revision) {
        checking = true; apk = null; release = null
        try { release = AppUpdates.latest(context); status = if (release == null) "Application à jour" else "Nouvelle version disponible" }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { status = e.message ?: "Vérification impossible" }
        finally { checking = false }
    }
    if (manual) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Mises à jour · ${BuildConfig.UPDATE_REPOSITORY}")
        Text(status, color = Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Action(if (checking) "Vérification…" else "Vérifier", enabled = !checking && !busy) { revision++ }
        }
    }
    release?.let { candidate -> AlertDialog(onDismissRequest = { if (!busy) release = null },
        title = { Text("Mise à jour ${candidate.version}") },
        text = { Text(if (busy) "Téléchargement et vérification de l’APK…" else if (apk != null) "APK prêt. Autorisez l’installation si Android le demande, puis appuyez sur Installer." else "Une nouvelle version de TvSama est disponible. $status") },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            scope.launch {
                busy = true
                try { val file = apk ?: AppUpdates.download(context, candidate).also { apk = it }; AppUpdates.install(context, file) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { status = e.message ?: "Échec de la mise à jour"; apk = null }
                finally { busy = false }
            }
        }) { Text(if (apk == null) "Mettre à jour" else "Installer") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { release = null }) { Text("Plus tard") } }) }
}
