package fr.nekotv

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun SourceDirectoryUi(onUpdated: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<DirectorySource>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var report by remember { mutableStateOf(StreamFlixProviderManager.getInstance().directoryReport()) }
    if (loading) { LinearProgressIndicator(Modifier.fillMaxWidth()); Action("Annuler") { job?.cancel(); loading = false } }
    Text("Annuaire OùStreamer", style = MaterialTheme.typography.titleLarge)
    Text("OùStreamer est prioritaire. Les portails officiels, AdressesDirect et le wiki FMHY servent de secours ; une adresse est mémorisée après validation du catalogue.", color = Muted)
    Action(if (loading) "Actualisation…" else if (error.isNotBlank()) "Réessayer l’annuaire" else "Actualiser l’annuaire", enabled = !loading) {
        job = scope.launch {
            loading = true
            error = ""
            try {
                val manager = StreamFlixProviderManager.getInstance()
                entries = manager.refreshDirectory()
                report = manager.directoryReport()
                onUpdated()
            }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Annuaire indisponible" }
            finally { loading = false }
        }
    }
    if (error.isNotBlank()) Text(error)
    report.forEach { (name, result) -> Text("$name : $result", color = if (result.startsWith("Adresse mise")) Accent else Muted) }
    if (entries.isNotEmpty()) {
        OutlinedTextField(query, { query = it }, label = { Text("Rechercher une adresse") }, singleLine = true)
        val matches = entries.filter { it.name.contains(query, true) }
        Text("${matches.size} adresses correspondantes · état annoncé par l’annuaire", color = Muted)
        if (matches.size > 20) Text("20 premières adresses affichées. Précisez la recherche pour voir les autres.", color = Muted)
        matches.take(20).forEach { entry ->
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text("${entry.name} · ${reportedDirectoryStatus(entry.status)}")
                Text("Signalé par ${entry.origin} · lecture non vérifiée", color = Muted)
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text("${entry.url}\n${entry.permanentUrl}", color = Muted)
                }
            }
        }
    }
}
