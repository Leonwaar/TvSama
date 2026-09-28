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
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class AppRelease(val version: String, val assetUrl: String, val size: Long, val digest: String)
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
        fun parts(s: String) = s.trim().removePrefix("v").removePrefix("V").split(".").map { it.toIntOrNull() ?: -1 }
        val a = parts(remote); val b = parts(local)
        if (a.any { it < 0 }) return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
    suspend fun latest(context: Context): AppRelease? = withContext(Dispatchers.IO) {
        check(BuildConfig.UPDATE_REPOSITORY.isNotBlank()) { "Dépôt GitHub absent de la configuration de compilation" }
        val conn = connection("https://api.github.com/repos/${BuildConfig.UPDATE_REPOSITORY}/releases/latest")
        try {
            check(conn.responseCode == 200) { if (conn.responseCode == 404) "Aucune release publique disponible sur le dépôt configuré." else "GitHub : HTTP ${conn.responseCode}" }
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val version = json.getString("tag_name")
            if (!newer(version, BuildConfig.VERSION_NAME)) return@withContext null
            val array = json.getJSONArray("assets")
            val apks = (0 until array.length()).map { array.getJSONObject(it) }.filter { it.getString("name").endsWith(".apk", true) }
            val asset = apks.firstOrNull { it.getString("name").contains("universal", true) } ?: apks.singleOrNull()
                ?: error("La release doit proposer un APK universel identifié")
            AppRelease(version, asset.getString("browser_download_url"), asset.getLong("size"), asset.optString("digest"))
        } finally { conn.disconnect() }
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
        if (release.digest.startsWith("sha256:")) {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input -> val buffer = ByteArray(65536); while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
            check(digest.digest().joinToString("") { "%02x".format(it) } == release.digest.substringAfter(":")) { "Empreinte APK incorrecte" }
        }
        @Suppress("DEPRECATION")
        val flags = PackageManager.GET_SIGNATURES
        @Suppress("DEPRECATION")
        val archive = context.packageManager.getPackageArchiveInfo(file.path, flags) ?: error("APK invalide")
        @Suppress("DEPRECATION")
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        check(archive.packageName == context.packageName) { "Cet APK ne correspond pas à TvSama" }
        @Suppress("DEPRECATION")
        check(archive.longVersion() > installed.longVersion()) { "Le versionCode de la release doit être supérieur" }
        @Suppress("DEPRECATION")
        check(archive.signatures?.toSet() == installed.signatures?.toSet() && !archive.signatures.isNullOrEmpty()) { "Signature incompatible avec la version installée" }
        file
    }
    private fun android.content.pm.PackageInfo.longVersion(): Long = if (Build.VERSION.SDK_INT >= 28) longVersionCode else @Suppress("DEPRECATION") versionCode.toLong()
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
    LaunchedEffect(revision) {
        try { release = AppUpdates.latest(context); status = if (release == null) "Application à jour" else "Nouvelle version disponible" }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { status = e.message ?: "Vérification impossible" }
    }
    if (manual) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Mises à jour · ${BuildConfig.UPDATE_REPOSITORY}")
        Text(status, color = Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Action("Vérifier") { revision++ }
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
