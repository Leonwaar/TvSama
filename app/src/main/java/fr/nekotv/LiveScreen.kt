package fr.nekotv

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.streamflixreborn.streamflix.providers.DirectEvent
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LiveScreen(tvOnly: Boolean = false, modifier: Modifier = Modifier, onPlay: (DirectEvent, List<VideoSource>) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var events by remember { mutableStateOf<List<DirectEvent>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    var calendarError by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf<String?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var playJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var fallback by remember { mutableStateOf<DirectEvent?>(null) }
    var readerEvent by remember { mutableStateOf<DirectEvent?>(null) }
    var readerOptions by remember { mutableStateOf<List<com.streamflixreborn.streamflix.models.Video.Server>>(emptyList()) }
    var readerLoading by remember { mutableStateOf(false) }
    fun play(event: DirectEvent, serverId: String? = null) {
        playJob?.cancel()
        playing = event.id
        playJob = scope.launch {
            try {
                error = ""
                onPlay(event, listOf(resolveLiveEvent(context, event.url, preferredServerId = serverId)))
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) { error = "Les lecteurs ne répondent pas."; fallback = event }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Lecture indisponible"; fallback = event }
            finally { playing = null }
        }
    }
    readerEvent?.let { event ->
        LaunchedEffect(event.id) {
            readerLoading = true; readerOptions = emptyList()
            try { readerOptions = when { FrenchLiveTv.owns(event.url) -> FrenchLiveTv.servers(event.url); StreamedLive.owns(event.url) -> StreamedLive.servers(event.url); else -> emptyList() } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Lecteurs indisponibles" }
            finally { readerLoading = false }
        }
        androidx.compose.ui.window.Dialog(onDismissRequest = { readerEvent = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.widthIn(max = if (context.isTelevision()) 720.dp else androidx.compose.ui.unit.Dp.Infinity)
                .fillMaxWidth().fillMaxHeight(if (context.isTelevision()) .85f else 1f), color = Ink) {
            Column(Modifier.safeDrawingPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Choisir le lecteur", style = MaterialTheme.typography.titleLarge)
                if (readerLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                else if (readerOptions.isEmpty()) Text("Aucun lecteur disponible. Réessayez.")
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(readerOptions, key = { it.src }) { server ->
                        Action(server.name, Modifier.fillMaxWidth()) { readerEvent = null; play(event, server.src) }
                    }
                }
                Action("Fermer", Modifier.fillMaxWidth()) { readerEvent = null }
            } }
        }
    }
    if (playing != null) LoadingDialog("Recherche d’un lecteur pour ce direct…") { playJob?.cancel(); playing = null }
    fallback?.let { event -> SourceAccess(event.title, event.url, { fallback = null }, { play(event) }, playback = true) }
    var revision by remember { mutableIntStateOf(0) }
    var reminderRevision by remember { mutableIntStateOf(0) }
    var pending by remember { mutableStateOf<DirectEvent?>(null) }
    fun schedule(event: DirectEvent) {
        val ok = LiveReminders.schedule(context, event.id, event.title, event.startsAt)
        error = if (ok) "Rappel programmé 10 minutes avant ${event.title}." else "Rappel impossible : vérifiez les autorisations ou l’horaire (au moins 10 minutes à l’avance)."
        reminderRevision++
    }
    val exactPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        pending?.let(::schedule); pending = null
    }
    fun exact(event: DirectEvent) {
        if (Build.VERSION.SDK_INT >= 31 && !context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) {
            pending = event
            exactPermission.launch(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
        } else schedule(event)
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        val event = pending; pending = null
        if (allowed && event != null) exact(event) else error = "Notifications refusées : aucun rappel activé."
    }
    LaunchedEffect(revision, tvOnly) {
        var directoryRefreshed = false
        while (true) {
            loading = true
            try {
                events = withTimeout(45_000) { kotlinx.coroutines.coroutineScope {
                    val french = async {
                        try { FrenchLiveTv.events(force = revision > 0 && !directoryRefreshed)
                            .filter { FrenchLiveTv.isSport(it) != tvOnly } }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { emptyList() }
                    }
                    val streamed = async {
                        try { if (!tvOnly) StreamedLive.events(force = revision > 0 && !directoryRefreshed) else emptyList() }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { emptyList() }
                    }
                    val frenchEvents = french.await()
                    if (frenchEvents.isNotEmpty()) events = frenchEvents
                    (frenchEvents + streamed.await()).distinctBy { it.id }
                } }
                calendarError = if (events.isEmpty()) "Aucun calendrier disponible. Réessayez l’actualisation." else ""
            }
            catch (e: kotlinx.coroutines.TimeoutCancellationException) { calendarError = "Le calendrier ne répond pas." }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { calendarError = e.message ?: "Calendrier indisponible" }
            finally { loading = false; directoryRefreshed = true }
            delay(60_000)
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(if (tvOnly) "TV française" else "Direct", if (tvOnly) "Chaînes françaises et adresses de secours" else "Sport en français · événements et lecteurs de secours")
        if (!tvOnly) Text(StreamedLive.status, color = Muted)
        OutlinedTextField(search, { search = it }, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(if (tvOnly) "Rechercher une chaîne…" else "Rechercher un direct…") },
            singleLine = true)
        Action(if (loading) "Actualisation…" else "Actualiser", enabled = !loading && playing == null) { revision++ }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error.isNotBlank()) Text(error, color = Muted)
        if (calendarError.isNotBlank()) Text(calendarError, color = Muted)
        val visible = filterLiveEvents(events, search)
        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            val now = System.currentTimeMillis()
            val live = visible.filter { it.isLive }
            val upcoming = visible.filter { !it.isLive && it.startsAt > now }
            if (search.isNotBlank()) item { Text("${visible.size} résultat${if (visible.size > 1) "s" else ""} pour « ${search.trim()} »", color = Muted) }
            item { Text("${if (tvOnly) "Chaînes" else "En direct"} (${live.size})", style = MaterialTheme.typography.titleLarge) }
            if (live.isEmpty()) item { Text(if (search.isNotBlank()) "Aucune chaîne ne correspond à cette recherche." else "Aucune diffusion signalée active par la source.", color = Muted) }
            items(live, key = { "live:${it.id}" }) { event ->
                Column(Modifier.fillMaxWidth()) {
                    Text(event.title); Text(event.competition, color = Muted)
                    Action(if (playing == event.id) "Chargement…" else "▶ Regarder", Modifier.fillMaxWidth(), enabled = playing == null) {
                        play(event)
                    }
                    Action("Choisir le lecteur", Modifier.fillMaxWidth(), enabled = playing == null) { readerEvent = event }
                }
            }
            if (!tvOnly) item { Text("À venir (${upcoming.size})", style = MaterialTheme.typography.titleLarge) }
            if (!tvOnly && upcoming.isEmpty()) item { Text("Aucun programme à venir publié.", color = Muted) }
            if (!tvOnly) items(upcoming, key = { "next:${it.id}" }) { event ->
                val active = remember(event.id, reminderRevision) { LiveReminders.has(context, event.id) }
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(event.title)
                        Text("${SimpleDateFormat("EEE dd/MM · HH:mm", Locale.FRANCE).format(Date(event.startsAt))} · ${event.competition}", color = Muted)
                    }
                    IconButton(onClick = {
                        if (active) { LiveReminders.cancel(context, event.id); reminderRevision++ }
                        else if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            pending = event; notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else exact(event)
                    }) { Icon(painterResource(R.drawable.ic_bell), contentDescription = if (active) "Annuler le rappel" else "Rappel 10 minutes avant", tint = if (active) Accent else Muted) }
                }
            }
        }
    }
}

private fun liveSearchKey(value: String): String = java.text.Normalizer.normalize(value.lowercase(), java.text.Normalizer.Form.NFD)
    .replace(Regex("\\p{M}"), "")
    .replace(Regex("[^a-z0-9]+"), " ")
    .trim()

/** Local search for the Direct and TV pages; independent from the catalogue search. */
internal fun filterLiveEvents(events: List<DirectEvent>, query: String): List<DirectEvent> {
    val needle = liveSearchKey(query)
    if (needle.isBlank()) return events
    return events.filter { event ->
        listOf(event.title, event.competition, event.url).any { liveSearchKey(it).contains(needle) }
    }
}
