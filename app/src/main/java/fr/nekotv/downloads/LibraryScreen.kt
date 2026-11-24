package fr.nekotv.downloads

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.streamflixreborn.streamflix.database.entities.DownloadJob

@Composable
fun LibraryScreen(viewModel: LibraryViewModel) {
    val libraryJobs by viewModel.libraryJobs.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Bibliothèque", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
        }
        items(libraryJobs) { job ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(job.title, style = MaterialTheme.typography.titleMedium)
                    Text("Qualité: ${job.quality} | Langue: ${job.language}")
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { viewModel.deleteJob(job) }) {
                            Text("Supprimer")
                        }
                    }
                }
            }
        }
    }
}
