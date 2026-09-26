package fr.nekotv

import android.content.Context
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.Glide

val Ink = Color(0xFF050505)
val Muted = Color(0xFFB8B8B8)
val Line = Color(0xFF303030)
val Accent = Color(0xFFC8F36B)
val Aqua = Color(0xFF71E4E8)
val Violet = Color(0xFFBCA7FF)
val Coral = Color(0xFFFF9A8B)
val Gold = Color(0xFFFFD166)

@Composable
fun SamaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color.White, onPrimary = Color.Black, background = Ink,
        surface = Ink, onSurface = Color.White, onBackground = Color.White,
        secondary = Accent, surfaceVariant = Color(0xFF141414), onSurfaceVariant = Muted,
        outline = Line
    ), typography = Typography(
        headlineLarge = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 34.sp),
        titleLarge = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
        bodyLarge = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 21.sp)
    ), content = content)
}

@Composable
fun Action(label: String, modifier: Modifier = Modifier, selected: Boolean = false, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val white = focused || primary
    Box(modifier.onFocusChanged { focused = it.isFocused }.focusable()
        .clip(RoundedCornerShape(4.dp))
        .background(if (white && enabled) Color.White else Color.Transparent)
        .border(if (focused || selected || primary) 2.dp else 1.dp, if (focused || selected || primary) Color.White else Line, RoundedCornerShape(4.dp))
        .clickable(enabled = enabled, onClick = onClick)
        .padding(horizontal = 18.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text(label, color = if (!enabled) Muted.copy(alpha = .45f) else if (white) Color.Black else Color.White,
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun Artwork(url: String, title: String, modifier: Modifier = Modifier, crop: Boolean = true) {
    Box(modifier.background(Color(0xFF151515)), contentAlignment = Alignment.Center) {
        Text(title.take(1).uppercase(), color = Line, fontSize = 56.sp, fontWeight = FontWeight.Bold)
        if (url.isNotBlank()) AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
            ImageView(context).apply { scaleType = if (crop) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER; contentDescription = title }
        }, update = { view -> Glide.with(view).load(url).into(view) }, onRelease = { Glide.with(it).clear(it) })
    }
}

@Composable
fun PosterCard(anime: Anime, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier.onFocusChanged { focused = it.isFocused }.focusable().clip(RoundedCornerShape(4.dp))
        .border(if (focused) 3.dp else 0.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(4.dp))
        .clickable(onClick = onClick).padding(5.dp)) {
        Artwork(anime.poster, anime.title, Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(3.dp)))
        Text(anime.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp).heightIn(min = 42.dp))
        Text(listOfNotNull(anime.tag, anime.year?.toString()).joinToString(" · "), color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ResumeCard(entry: SavedPlayback, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(190.dp).onFocusChanged { focused = it.isFocused }.focusable().clip(RoundedCornerShape(5.dp))
        .border(if (focused) 3.dp else 1.dp, if (focused) Color.White else Line, RoundedCornerShape(5.dp))
        .clickable(onClick = onClick).padding(6.dp)) {
        Artwork(entry.anime.poster, entry.anime.title, Modifier.fillMaxWidth().height(92.dp).clip(RoundedCornerShape(3.dp)))
        Text(entry.anime.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
        Text(entry.episodeTitle, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        androidx.compose.material3.LinearProgressIndicator(progress = { (entry.position.toFloat() / entry.duration.coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 7.dp), color = Accent, trackColor = Line)
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String = "") {
    Text(title, style = MaterialTheme.typography.headlineLarge, color = Color.White)
    if (subtitle.isNotBlank()) Text(subtitle, color = Muted, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
}

@Composable
fun EmptyState(title: String, explanation: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(explanation, color = Muted)
    }
}

@Composable
fun CategoryRail(selected: String, onSelected: (String) -> Unit) {
    val categories = listOf(
        "Tous" to Accent,
        "Films" to Coral,
        "Séries" to Aqua,
        "Animation" to Violet,
        "VF" to Color(0xFF9BE564),
        "VOSTFR" to Color(0xFFFFB86B),
        "TV en direct" to Gold
    )
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
        items(categories) { (label, color) ->
            var focused by remember { mutableStateOf(false) }
            Box(Modifier.widthIn(min = 112.dp).onFocusChanged { focused = it.isFocused }.focusable()
                .clip(RoundedCornerShape(6.dp)).background(if (selected == label || focused) color else color.copy(alpha = .14f))
                .border(if (selected == label || focused) 2.dp else 1.dp, color, RoundedCornerShape(6.dp))
                .clickable { onSelected(label) }.padding(horizontal = 14.dp, vertical = 13.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(if (label == "Tous") "✦" else if (label == "TV en direct") "◉" else "●", color = if (selected == label || focused) Ink else color, fontSize = 14.sp)
                    Text(label, color = if (selected == label || focused) Ink else Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun PreferenceCard(title: String, description: String, color: Color, content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = .10f)).border(1.dp, color.copy(alpha = .45f), RoundedCornerShape(8.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(title, fontWeight = FontWeight.SemiBold); Text(description, color = Muted, fontSize = 12.sp) }
        content()
    }
}

@Composable
fun TvSettings(language: String, onLanguage: (String) -> Unit, autoplay: Boolean, onAutoplay: (Boolean) -> Unit, onClearHistory: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("tvsama_settings", Context.MODE_PRIVATE) }
    val pairedDevice = prefs.getString("paired_tv_device", null)
    var startup by remember { mutableStateOf(prefs.getString("startup", "Accueil") ?: "Accueil") }
    var density by remember { mutableStateOf(prefs.getString("density", "Confort") ?: "Confort") }
    var motion by remember { mutableStateOf(prefs.getBoolean("motion", true)) }
    var subtitles by remember { mutableStateOf(prefs.getBoolean("subtitles", true)) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle("Réglages TV", "Personnalisez votre expérience TvSama depuis le canapé.")
        Text("Lecture", style = MaterialTheme.typography.titleLarge)
        PreferenceCard("Langue des versions", "Les sources non vérifiées VF/VOSTFR sont masquées.", Aqua) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Toutes", "VF", "VOSTFR").forEach { value -> Action(if (value == "Toutes") "VF + VOSTFR" else value, selected = language == value) { onLanguage(value) } } }
        }
        PreferenceCard("Épisode suivant", "Lance automatiquement l’épisode suivant quand il existe.", Accent) {
            Action(if (autoplay) "Activé" else "Manuel", selected = autoplay) { onAutoplay(!autoplay) }
        }
        PreferenceCard("Sous-titres français", "Active la piste française par défaut en VOSTFR.", Violet) {
            Action(if (subtitles) "Activés" else "Désactivés", selected = subtitles) { subtitles = !subtitles; prefs.edit().putBoolean("subtitles", subtitles).apply() }
        }
        Text("Interface", style = MaterialTheme.typography.titleLarge)
        PreferenceCard("Écran de démarrage", "Choisissez la page ouverte au lancement.", Gold) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Accueil", "Reprendre", "Ma liste").forEach { value -> Action(value, selected = startup == value) { startup = value; prefs.edit().putString("startup", value).apply() } } }
        }
        PreferenceCard("Densité des affiches", "Plus grand pour une TV éloignée, compact pour parcourir davantage.", Coral) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Confort", "Compact").forEach { value -> Action(value, selected = density == value) { density = value; prefs.edit().putString("density", value).apply() } } }
        }
        PreferenceCard("Animations de focus", "Conserve les transitions et contours de navigation TV.", Aqua) {
            Action(if (motion) "Activées" else "Réduites", selected = motion) { motion = !motion; prefs.edit().putBoolean("motion", motion).apply() }
        }
        Text("Télécommande et Cast", style = MaterialTheme.typography.titleLarge)
        Text("Associez un téléphone en scannant le QR code, puis choisissez le téléviseur Cast sur le téléphone.", color = Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) { CastRouteButton(Modifier.width(190.dp).height(48.dp)); Text("Le bouton télécommande ouvre l’association QR.", color = Muted, fontSize = 12.sp) }
        if (!pairedDevice.isNullOrBlank()) Text("Téléviseur associé : $pairedDevice", color = Accent, fontSize = 13.sp)
        HorizontalDivider(color = Line)
        Text("Bibliothèque", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Action("Effacer l’historique") { onClearHistory() }; Action("Réinitialiser les préférences") { prefs.edit().clear().apply() } }
        AdvancedSettings()
        Text("TvSama · Interface indépendante. Les intégrations de fournisseurs viennent de StreamFlix Reborn sous licence Apache 2.0.", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(24.dp))
    }
}
