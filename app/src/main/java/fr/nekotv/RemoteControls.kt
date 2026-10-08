package fr.nekotv

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Composable
internal fun RemoteControls() {
    val context = LocalContext.current
    val state by RemoteLink.snapshot.collectAsState()
    val playback = state.playback
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    fun command(action: String, position: Long = 0) { scope.launch {
        try { RemoteLink.send(context, RemoteCommand(action = action, position = position)); error = "" }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "Télévision injoignable. Vérifiez la connexion." }
    } }
    val entries = playback.anime?.episodes.orEmpty().distinctBy { it.seasonNumber to it.number }
    val index = entries.indexOfFirst { it.id == playback.episode?.id }
    LaunchedEffect(playback.anime?.id, playback.episode?.id) {
        val anime = playback.anime ?: return@LaunchedEffect
        if (index < 0) return@LaunchedEffect
        delay(5000)
        while (true) {
            for (target in listOfNotNull(entries.getOrNull(index + 1), entries.getOrNull(index - 1))) {
                try {
                    val language = LibraryStore(context).language()
                    val videos = StreamFlixProviderManager.getInstance().resolveSources(anime, target, language)
                    if (videos.isNotEmpty()) RemoteLink.send(context, RemoteCommand(action = "preload", anime = anime,
                        episode = target, sources = videos, language = language))
                }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { }
            }
            delay(90_000)
        }
    }
    fun playEpisode(target: Episode) { scope.launch {
        busy = true
        try {
            val anime = playback.anime ?: return@launch
            val sources = StreamFlixProviderManager.getInstance().resolveSources(anime, target, LibraryStore(context).language())
            check(sources.isNotEmpty())
            RemoteLink.send(context, RemoteCommand(action = "play", anime = anime, episode = target, sources = sources, language = LibraryStore(context).language()))
            error = ""
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "Impossible de lancer cet épisode sur la télévision." }
        finally { busy = false }
    } }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Action(if (expanded) "Masquer la télécommande TV" else "Télécommande TV · ${playback.title.ifBlank { "Connectée" }}") { expanded = !expanded }
        if (expanded) {
            Text(playback.title.ifBlank { "Choisissez un média sur votre téléphone." })
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Action("−30 s") { command("seek", (playback.position - 30000).coerceAtLeast(0)) }
                Action(if (playback.playing) "Pause" else "Lecture") { command("toggle") }
                Action("+30 s") { command("seek", playback.position + 30000) }
                Action("Relancer 3 h") { command("sleep") }
            }
            if (playback.duration > 0) {
                Slider(value = dragging ?: playback.position.toFloat().coerceIn(0f, playback.duration.toFloat()),
                    onValueChange = { dragging = it }, valueRange = 0f..playback.duration.toFloat(),
                    onValueChangeFinished = { dragging?.let { command("seek", it.toLong()) }; dragging = null })
                Text("${(dragging?.toLong() ?: playback.position) / 60000} / ${playback.duration / 60000} min")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Action("Précédent", enabled = !busy && index > 0) { playEpisode(entries[index - 1]) }
                Action("Suivant", enabled = !busy && index >= 0 && index + 1 < entries.size) { playEpisode(entries[index + 1]) }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(entries, key = { "${it.seasonNumber}:${it.number}" }) { entry ->
                    Action("S${entry.seasonNumber} E${entry.number}", selected = entry.id == playback.episode?.id, enabled = !busy) { playEpisode(entry) }
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (error.isNotBlank()) Text(error)
    }
}
