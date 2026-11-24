package fr.nekotv.downloads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.nekotv.VideoSource

@Composable
fun DownloadOptionsDialog(
    sources: List<VideoSource>,
    onConfirm: (VideoSource) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedSource by remember { mutableStateOf<VideoSource?>(sources.firstOrNull()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Options de téléchargement") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Choisissez une source :", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(sources) { source ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedSource = source }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (selectedSource == source),
                                onClick = { selectedSource = source }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("${source.name} (${source.quality})", style = MaterialTheme.typography.bodyMedium)
                                Text("Langue: ${source.language} | Provider: ${source.provider}", 
                                      style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    selectedSource?.let { onConfirm(it) }
                },
                enabled = selectedSource != null
            ) {
                Text("Télécharger")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}
