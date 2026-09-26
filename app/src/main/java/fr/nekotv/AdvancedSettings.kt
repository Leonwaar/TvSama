package fr.nekotv

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

/** Local, user-selected backup files never contain cloud credentials. */
private val backupPreferenceFiles = listOf("sources", "tvsama_library", "tvsama_settings")

@Composable
fun AdvancedSettings() {
    val context = LocalContext.current
    var feedback by remember { mutableStateOf("") }
    var pendingRestore by remember { mutableStateOf<JSONObject?>(null) }
    var showLicenses by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            feedback = runCatching {
                val files = JSONObject()
                backupPreferenceFiles.forEach { name ->
                    val entries = JSONObject()
                    context.getSharedPreferences(name, Context.MODE_PRIVATE).all.forEach { (key, value) ->
                        val type = when (value) {
                            is String -> "string"
                            is Boolean -> "boolean"
                            is Int -> "int"
                            is Long -> "long"
                            is Float -> "float"
                            is Set<*> -> "strings"
                            else -> return@forEach
                        }
                        entries.put(key, JSONObject().put("type", type).put("value", if (value is Set<*>) JSONArray(value.toList()) else value))
                    }
                    files.put(name, entries)
                }
                val data = JSONObject().put("app", "TvSama").put("version", 1).put("preferences", files)
                requireNotNull(context.contentResolver.openOutputStream(uri)).bufferedWriter().use { it.write(data.toString(2)) }
                "Sauvegarde enregistrée."
            }.getOrElse { "Sauvegarde impossible : ${it.localizedMessage}" }
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val bytes = requireNotNull(context.contentResolver.openInputStream(uri)).use { it.readBytesWithLimit(4 * 1024 * 1024) }
                val data = JSONObject(bytes.toString(Charsets.UTF_8))
                require(data.optString("app") == "TvSama" && data.optInt("version") == 1) { "Format de sauvegarde non reconnu" }
                val files = data.getJSONObject("preferences")
                // Validate the entire document before changing any preference.
                backupPreferenceFiles.forEach { name ->
                    files.optJSONObject(name)?.let { entries ->
                        entries.keys().forEach { key -> validateEntry(entries.getJSONObject(key)) }
                    }
                }
                pendingRestore = files
            }.onFailure { feedback = "Restauration impossible : ${it.localizedMessage}" }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Sauvegarde et informations", style = MaterialTheme.typography.titleLarge)
        Text("Exportez vos favoris, votre historique et vos sources dans un fichier personnel.")
        OutlinedButton(onClick = { export.launch("TvSama-sauvegarde.json") }) { Text("Exporter ma bibliothèque") }
        OutlinedButton(onClick = { restore.launch(arrayOf("application/json", "text/plain")) }) { Text("Restaurer une sauvegarde") }
        OutlinedButton(onClick = { showLicenses = true }) { Text("Crédits et licences") }
        if (feedback.isNotEmpty()) Text(feedback)
    }
    pendingRestore?.let { files ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restaurer la bibliothèque ?") },
            text = { Text("Les favoris, l’historique et les sources présents dans cette sauvegarde remplaceront les données locales correspondantes. Relancez ensuite TvSama.") },
            confirmButton = {
                TextButton(onClick = {
                    feedback = runCatching {
                        backupPreferenceFiles.forEach { name ->
                            val entries = files.optJSONObject(name) ?: return@forEach
                            val edit = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
                            entries.keys().forEach { key ->
                                val entry = entries.getJSONObject(key)
                                when (entry.getString("type")) {
                                    "string" -> edit.putString(key, entry.getString("value"))
                                    "boolean" -> edit.putBoolean(key, entry.getBoolean("value"))
                                    "int" -> edit.putInt(key, entry.getInt("value"))
                                    "long" -> edit.putLong(key, entry.getLong("value"))
                                    "float" -> edit.putFloat(key, entry.getDouble("value").toFloat())
                                    "strings" -> edit.putStringSet(key, entry.getJSONArray("value").let { array -> (0 until array.length()).map { array.getString(it) }.toSet() })
                                }
                            }
                            check(edit.commit()) { "Écriture impossible" }
                        }
                        "Sauvegarde restaurée. Fermez puis relancez TvSama pour charger les données."
                    }.getOrElse { "Restauration impossible : ${it.localizedMessage}" }
                    pendingRestore = null
                }) { Text("Restaurer") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Annuler") } },
        )
    }
    if (showLicenses) {
        val notice = remember {
            listOf("licenses/NOTICE.txt", "licenses/streamflix-Apache-2.0.txt").joinToString("\n\n") { path ->
                context.assets.open(path).bufferedReader().use { it.readText() }
            }
        }
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text("TvSama · Crédits et licences") },
            text = { Text(notice, modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { showLicenses = false }) { Text("Fermer") } },
        )
    }
}

private fun validateEntry(entry: JSONObject) {
    when (entry.getString("type")) {
        "string" -> require(entry.get("value") is String)
        "boolean" -> require(entry.get("value") is Boolean)
        "int" -> entry.getInt("value")
        "long" -> entry.getLong("value")
        "float" -> require(entry.getDouble("value").toFloat().isFinite())
        "strings" -> entry.getJSONArray("value").let { values -> (0 until values.length()).forEach { require(values.get(it) is String) } }
        else -> error("Type de préférence non reconnu")
    }
}

private fun java.io.InputStream.readBytesWithLimit(limit: Int): ByteArray {
    val buffer = java.io.ByteArrayOutputStream()
    val chunk = ByteArray(8192)
    while (true) {
        val count = read(chunk)
        if (count < 0) break
        require(buffer.size() + count <= limit) { "Sauvegarde trop volumineuse" }
        buffer.write(chunk, 0, count)
    }
    return buffer.toByteArray()
}
