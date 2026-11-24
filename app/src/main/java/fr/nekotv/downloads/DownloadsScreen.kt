package fr.nekotv.downloads

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import fr.nekotv.OfflineDownloads
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun DownloadsScreen(context: android.content.Context) {
    var downloads by remember { mutableStateOf(emptyList<Download>()) }

    LaunchedEffect(Unit) {
        while (true) {
            downloads = OfflineDownloads.getAllDownloads(context)
            delay(1000)
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Téléchargements", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
        }
        items(downloads) { download ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Téléchargement: ${download.request.id}")
                    LinearProgressIndicator(progress = { download.percentDownloaded / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("État: ${download.state}")
                }
            }
        }
    }
}
