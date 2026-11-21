package fr.nekotv

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.delay
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun RemoteControls(onBack: () -> Unit) {
    val context = LocalContext.current
    val target by RemoteLink.target.collectAsState()
    val state by RemoteLink.snapshot.collectAsState()
    val playback = state.playback
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf("") }
    var dragging by remember { mutableStateOf<Float?>(null) }
    if (target == null) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Télécommande", "Associez d’abord votre télévision par QR code.")
            CastRouteButton(Modifier.size(56.dp))
            Action("Retour", onClick = onBack)
        }
        return
    }
    fun command(action: String, position: Long = 0, key: String = "") { scope.launch {
        try { RemoteLink.send(context, RemoteCommand(action = action, position = position, query = key)); error = "" }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "Télévision injoignable. Vérifiez le Wi-Fi et que TvSama est ouvert." }
    } }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionTitle("Télécommande", playback.title.ifBlank { "Télévision liée · choisissez un programme" })
        if (error.isNotBlank()) Text(error, color = Coral)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Action("← Retour") { command("key", key = "back") }
            Action("Accueil TV") { command("key", key = "home") }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            RemoteKey("Haut", R.drawable.ic_remote_up) { command("key", key = "up") }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RemoteKey("Gauche", R.drawable.ic_remote_left) { command("key", key = "left") }
                Action("OK", Modifier.size(80.dp), primary = true) { command("key", key = "ok") }
                RemoteKey("Droite", R.drawable.ic_remote_right) { command("key", key = "right") }
            }
            RemoteKey("Bas", R.drawable.ic_remote_down) { command("key", key = "down") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RemoteSeekKey("Reculer de 15 secondes, maintenir pour accélérer", R.drawable.ic_remote_rewind) { command("seek_by", -it) }
            IconAction(if (playback.playing) R.drawable.ic_remote_pause else R.drawable.ic_remote_play,
                if (playback.playing) "Pause" else "Lecture", Modifier.size(80.dp), iconSize = 34.dp) { command("toggle") }
            RemoteSeekKey("Avancer de 15 secondes, maintenir pour accélérer", R.drawable.ic_remote_forward) { command("seek_by", it) }
        }
        Action("Minuteur 30 min") { command("sleep", position = 30) }
        if (playback.duration > 0) {
            Column(Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
                Slider(value = (dragging ?: playback.position.toFloat()).coerceIn(0f, playback.duration.toFloat()),
                    onValueChange = { dragging = it }, valueRange = 0f..playback.duration.toFloat(),
                    onValueChangeFinished = { dragging?.let { command("seek", it.toLong()) }; dragging = null })
                Text("${(dragging?.toLong() ?: playback.position) / 60_000} / ${playback.duration / 60_000} min", color = Muted)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Action("Précédent", enabled = playback.hasPrevious) { command("previous") }
            Action("Suivant", enabled = playback.hasNext) { command("next") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Action("Son −") { command("key", key = "volume_down") }
            Action("Son +") { command("key", key = "volume_up") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CastRouteButton(Modifier.size(52.dp))
            Action("Délier") { RemoteLink.disconnect(context); onBack() }
            Action("Fermer", onClick = onBack)
        }
    }
}

@Composable
private fun RemoteKey(description: String, icon: Int, onClick: () -> Unit) =
    IconAction(icon, description, Modifier.size(64.dp), tint = Color.White, onClick = onClick)

@Composable
private fun RemoteSeekKey(description: String, icon: Int, onSeek: (Long) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val callback by rememberUpdatedState(onSeek)
    LaunchedEffect(pressed) {
        if (pressed) {
            val started = android.os.SystemClock.elapsedRealtime()
            delay(android.view.ViewConfiguration.getLongPressTimeout().toLong() + 100)
            while (true) {
                callback(seekIncrement(android.os.SystemClock.elapsedRealtime() - started))
                delay(250)
            }
        }
    }
    Box(Modifier.size(64.dp).background(if (pressed) Color(0xFF454545) else Color(0xFF171717), RoundedCornerShape(12.dp))
        .border(1.dp, Line, RoundedCornerShape(12.dp))
        .semantics { contentDescription = description }
        .combinedClickable(interactionSource = interaction, indication = androidx.compose.foundation.LocalIndication.current,
            role = Role.Button, onClick = { callback(15_000) }, onLongClick = {}), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), contentDescription = null, tint = Color.White)
    }
}
