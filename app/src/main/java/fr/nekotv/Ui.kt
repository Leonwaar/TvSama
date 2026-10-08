package fr.nekotv

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import android.content.Context
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
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

private val ThemeBackground = compositionLocalOf { Color(0xFF050505) }
val Ink: Color @Composable get() = ThemeBackground.current
val Muted = Color(0xFFB8B8B8)
val Line = Color(0xFF303030)
val Accent = Color(0xFFC8F36B)
val Aqua = Color(0xFF71E4E8)
val Violet = Color(0xFFBCA7FF)
val Coral = Color(0xFFFF9A8B)
val Gold = Color(0xFFFFD166)

@Composable
fun SamaTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("tvsama_settings", 0) }
    var oled by remember { mutableStateOf(preferences.getBoolean("oled", false)) }
    DisposableEffect(preferences) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "oled") oled = preferences.getBoolean("oled", false)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    CompositionLocalProvider(ThemeBackground provides if (oled) Color.Black else Color(0xFF141414)) {
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
}

@Composable
fun Action(label: String, modifier: Modifier = Modifier, selected: Boolean = false, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val white = focused || primary
    val background by animateColorAsState(if (white && enabled) Color.White else Color.Transparent,
        animationSpec = tween(120), label = "actionBackground")
    val foreground by animateColorAsState(if (!enabled) Muted.copy(alpha = .45f) else if (white) Color.Black else Color.White,
        animationSpec = tween(120), label = "actionForeground")
    Box(modifier.onFocusChanged { focused = it.isFocused }
        .clip(RoundedCornerShape(4.dp))
        .background(background)
        .border(if (focused || selected || primary) 2.dp else 1.dp, if (focused || selected || primary) Color.White else Line, RoundedCornerShape(4.dp))
        .clickable(enabled = enabled, onClick = onClick)
        .padding(horizontal = 18.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text(label, color = foreground,
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun Artwork(url: String, title: String, modifier: Modifier = Modifier, crop: Boolean = true) {
    Box(modifier.background(Color(0xFF151515)), contentAlignment = Alignment.Center) {
        Text(title.take(1).uppercase(), color = Line, fontSize = 56.sp, fontWeight = FontWeight.Bold)
        if (url.isNotBlank()) AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
            ImageView(context).apply { scaleType = if (crop) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER; contentDescription = title }
        }, update = { view ->
            view.contentDescription = title
            view.scaleType = if (crop) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
            Glide.with(view).load(com.streamflixreborn.streamflix.utils.SourceAddresses.rewrite(url))
                .error(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)).into(view)
        }, onRelease = { Glide.with(it).clear(it) })
    }
}

@Composable
fun PosterCard(anime: Anime, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.015f else 1f, tween(140), label = "posterFocus")
    Column(modifier.graphicsLayer { scaleX = scale; scaleY = scale }.onFocusChanged { focused = it.isFocused }.clip(RoundedCornerShape(4.dp))
        .border(if (focused) 3.dp else 0.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(4.dp))
        .clickable(onClick = onClick).padding(5.dp)) {
        Artwork(anime.poster, anime.title, Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(3.dp)))
        Text(anime.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp).heightIn(min = 42.dp))
        Text(listOfNotNull(anime.tag, anime.year?.toString()).joinToString(" · "), color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ResumeCard(entry: SavedPlayback, onRemove: () -> Unit, onClick: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    if (menu) AlertDialog(onDismissRequest = { menu = false }, title = { Text(entry.anime.title) },
        text = { Text("Retirer ce titre de Reprendre ? La progression des épisodes sera conservée.") },
        confirmButton = { TextButton(onClick = { onRemove(); menu = false }) { Text("Retirer") } },
        dismissButton = { TextButton(onClick = { menu = false }) { Text("Annuler") } })
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(190.dp).onFocusChanged { focused = it.isFocused }.clip(RoundedCornerShape(5.dp))
        .border(if (focused) 3.dp else 1.dp, if (focused) Color.White else Line, RoundedCornerShape(5.dp))
        .combinedClickable(onClick = onClick, onLongClick = { menu = true }).padding(6.dp)) {
        Artwork(entry.anime.poster, entry.anime.title, Modifier.fillMaxWidth().height(92.dp).clip(RoundedCornerShape(3.dp)))
        Text(entry.anime.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
        Text(if (entry.anime.tag == "Film") "Film" else if (entry.season > 0) "S${entry.season} · E${entry.number} — ${entry.episodeTitle}" else entry.episodeTitle, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        "TV en direct" to Gold,
        "Diffusions en direct" to Gold
    )
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
        items(categories) { (label, color) ->
            var focused by remember { mutableStateOf(false) }
            Box(Modifier.widthIn(min = 112.dp).onFocusChanged { focused = it.isFocused }.focusable()
                .clip(RoundedCornerShape(6.dp)).background(if (selected == label || focused) color else Color.Transparent)
                .border(if (selected == label || focused) 2.dp else 1.dp, color, RoundedCornerShape(6.dp))
                .clickable { onSelected(label) }.padding(horizontal = 14.dp, vertical = 13.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(if (label == "Tous") "✦" else if (label.contains("direct", true)) "◉" else "●", color = if (selected == label || focused) Ink else color, fontSize = 14.sp)
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
    val timer = remember { SleepTimer(context) }
    var sleepMinutes by remember { mutableIntStateOf(timer.minutes()) }
    var autoDim by remember { mutableStateOf(prefs.getBoolean("pause_dimming", true)) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle("Réglages TV", "Personnalisez votre expérience TvSama depuis le canapé.")
        Text("Lecture", style = MaterialTheme.typography.titleLarge)
        PreferenceCard("Minuterie automatique", "Met la lecture en pause après la durée choisie. Le chrono continue au changement d’épisode, de film ou de serveur.", Gold) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (listOf(0) + SleepTimer.options).forEach { minutes ->
                    Action(if (minutes == 0) "Désactivée" else SleepTimer.label(minutes), selected = sleepMinutes == minutes) {
                        sleepMinutes = minutes; timer.configure(minutes)
                    }
                }
            }
        }
        PreferenceCard("Épisode suivant", "Lance automatiquement l’épisode suivant quand il existe.", Accent) {
            Action(if (autoplay) "Activé" else "Manuel", selected = autoplay) { onAutoplay(!autoplay) }
        }
        PreferenceCard("Assombrissement automatique", "Assombrit fortement l’écran après 30 secondes en pause. Un toucher, un mouvement ou une touche rétablit l’image.", Gold) {
            Action(if (autoDim) "Activé" else "Désactivé", selected = autoDim) { autoDim = !autoDim; prefs.edit().putBoolean("pause_dimming", autoDim).apply() }
        }
        PreferenceCard("Sous-titres français", "Active la piste française par défaut en VOSTFR.", Violet) {
            Action(if (subtitles) "Activés" else "Désactivés", selected = subtitles) { subtitles = !subtitles; prefs.edit().putBoolean("subtitles", subtitles).apply() }
        }
        Text("Interface", style = MaterialTheme.typography.titleLarge)
        PreferenceCard("Fond OLED", "Choisissez un noir pur ou un gris sombre.", Violet) {
            Action(if (prefs.getBoolean("oled", false)) "Noir OLED" else "Sombre") {
                prefs.edit().putBoolean("oled", !prefs.getBoolean("oled", false)).apply()
            }
        }
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
        Text("Scannez le QR de la TV avec votre téléphone : recherches, lecture et progression sont partagées sur le même Wi-Fi.", color = Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) { CastRouteButton(Modifier.width(190.dp).height(48.dp)); Text("Le bouton télécommande ouvre l’association QR.", color = Muted, fontSize = 12.sp) }
        if (!pairedDevice.isNullOrBlank()) Text("Téléviseur associé : $pairedDevice", color = Accent, fontSize = 13.sp)
        HorizontalDivider(color = Line)
        Text("Bibliothèque", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Action("Effacer l’historique") { onClearHistory() }; Action("Réinitialiser les préférences") { prefs.edit().clear().apply() } }
        var tmdbKey by remember { mutableStateOf("") }
        Text("Métadonnées TMDB", style = MaterialTheme.typography.titleLarge)
        Text("Movix utilise les pages publiques TMDB sans clé. Une clé personnelle reste facultative pour l’API de métadonnées.", color = Muted, fontSize = 13.sp)
        OutlinedTextField(tmdbKey, { tmdbKey = it }, label = { Text("Clé API TMDB personnelle") }, singleLine = true,
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
        Action("Enregistrer la clé TMDB") {
            com.streamflixreborn.streamflix.utils.UserPreferences.tmdbApiKey = tmdbKey.trim(); tmdbKey = ""
        }
        UpdatePrompt(manual = true)
        AdvancedSettings()
        Text("TvSama · Interface indépendante. Les intégrations de fournisseurs viennent de StreamFlix Reborn sous licence Apache 2.0.", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(24.dp))
    }
}
