package fr.nekotv

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun SourceDirectoryUi() {
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<DirectorySource>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    Text("Annuaire OùStreamer", style = MaterialTheme.typography.titleLarge)
    Text("L’actualisation remplace et mémorise les adresses utilisées par les sources intégrées.", color = Muted)
    Action(if (loading) "Actualisation…" else "Actualiser l’annuaire", enabled = !loading) {
        scope.launch {
            loading = true
            error = ""
            try { entries = StreamFlixProviderManager.getInstance().refreshDirectory() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Annuaire indisponible" }
            finally { loading = false }
        }
    }
    if (error.isNotBlank()) Text(error)
    if (entries.isNotEmpty()) {
        OutlinedTextField(query, { query = it }, label = { Text("Rechercher une adresse") }, singleLine = true)
        Text("${entries.size} adresses françaises · état annoncé par l’annuaire", color = Muted)
        entries.filter { it.name.contains(query, true) }.take(20).forEach { entry ->
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text("${entry.name} · ${entry.status}")
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text("${entry.url}\n${entry.permanentUrl}", color = Muted)
                }
            }
        }
    }
}
