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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.streamflixreborn.streamflix.providers.DirectEvent
import com.streamflixreborn.streamflix.providers.VolkaMaxProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LiveScreen(onPlay: (DirectEvent, List<VideoSource>) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var events by remember { mutableStateOf<List<DirectEvent>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf<String?>(null) }
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
    LaunchedEffect(revision) {
        while (true) {
            loading = true
            try { events = withTimeout(25_000) { VolkaMaxProvider.events() }; error = "" }
            catch (e: kotlinx.coroutines.TimeoutCancellationException) { error = "Le calendrier ne répond pas." }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Calendrier indisponible" }
            finally { loading = false }
            delay(60_000)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Directs sportifs", "Volkamax · En cours et programmes à venir")
        Action("Actualiser", enabled = !loading) { revision++ }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error.isNotBlank()) Text(error, color = Muted)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            val now = System.currentTimeMillis()
            val live = events.filter { it.isLive }
            val upcoming = events.filter { !it.isLive && it.startsAt > now }
            item { Text("En direct (${live.size})", style = MaterialTheme.typography.titleLarge) }
            if (live.isEmpty()) item { Text("Aucune diffusion signalée active par la source.", color = Muted) }
            items(live, key = { "live:${it.id}" }) { event ->
                Column {
                    Text(event.title); Text(event.competition, color = Muted)
                    Action(if (playing == event.id) "Chargement…" else "▶ Regarder", enabled = playing == null) {
                        scope.launch {
                            playing = event.id
                            try {
                                error = ""
                                val videos = withTimeout(45_000) {
                                    val found = mutableListOf<VideoSource>()
                                    for (server in VolkaMaxProvider.servers(event.url).distinctBy { it.src }.take(6)) {
                                        try {
                                            val video = withTimeout(12_000) { VolkaMaxProvider.video(server) }
                                            if (Uri.parse(video.source).scheme in listOf("http", "https")) {
                                                found += VideoSource(server.name, video.source, "UNKNOWN", "Auto", VolkaMaxProvider.NAME, video.headers.orEmpty(), mimeType = video.type)
                                                break // Start playback as soon as a working extractor returns.
                                            }
                                        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                                        } catch (e: CancellationException) { throw e }
                                        catch (_: Exception) { }
                                    }
                                    found.toList()
                                }
                                check(videos.isNotEmpty()) { "Aucun lecteur compatible disponible pour ce direct." }
                                onPlay(event, videos)
                            } catch (e: kotlinx.coroutines.TimeoutCancellationException) { error = "Le lecteur ne répond pas." }
                            catch (e: CancellationException) { throw e }
                            catch (e: Exception) { error = e.message ?: "Lecture indisponible" }
                            finally { playing = null }
                        }
                    }
                }
            }
            item { Text("À venir (${upcoming.size})", style = MaterialTheme.typography.titleLarge) }
            if (upcoming.isEmpty()) item { Text("Aucun programme à venir publié.", color = Muted) }
            items(upcoming, key = { "next:${it.id}" }) { event ->
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
