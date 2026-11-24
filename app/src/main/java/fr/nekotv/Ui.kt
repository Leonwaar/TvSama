package fr.nekotv

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.graphics.graphicsLayer
import android.content.Context
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.LocalIndication
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import kotlinx.coroutines.launch

private val ThemeBackground = compositionLocalOf { Color(0xFF050505) }
val MotionEnabled = compositionLocalOf { true }
val CompactPosters = compositionLocalOf { false }
/** "VF" shows French titles, "VOSTFR" the original (romanised when Japanese). */
val LocalTitleLanguage = compositionLocalOf { "VF" }
val Ink: Color @Composable get() = ThemeBackground.current
val Muted = Color(0xFFB8B8B8)
val Line = Color(0xFF303030)
val SourceGreen = Color(0xFF68DE96)
val Accent = Color(0xFFC8F36B)
val Aqua = Color(0xFF71E4E8)
val Violet = Color(0xFFBCA7FF)
val Coral = Color(0xFFFF9A8B)
val Gold = Color(0xFFFFD166)

private val CATEGORY_ITEMS = listOf(
    "Tous" to Accent,
    "Films" to Coral,
    "Séries" to Aqua,
    "Animation" to Violet,
    "TV" to Gold,
    "Diffusions en direct" to Gold
)

/** Maximum decoded dimensions for poster artwork to keep the heap footprint sane on 1 GB projectors. */
private const val POSTER_DECODE_WIDTH = 320
private const val POSTER_DECODE_HEIGHT = 480

/** One readable line per source; never repeats the provider or the version twice. */
internal fun sourceLabel(video: VideoSource, fallbackLanguage: String): String {
    val version = video.language.takeIf { it in listOf("VF", "VOSTFR") } ?: fallbackLanguage
    val base = if (video.name.startsWith(video.provider, ignoreCase = true) || video.provider.isBlank()) video.name
        else listOf(video.provider, video.name).filter { it.isNotBlank() }.joinToString(" · ")
    val transport = when (video.mimeType) {
        VIDRIFT_MIME_TYPE -> "Lecteur intégré"
        else -> "${video.latencyMs.takeIf { it > 0 } ?: "?"} ms"
    }
    return listOf("▶ $base", version.takeUnless { base.contains(it, true) }.orEmpty(), video.quality, transport)
        .filter { it.isNotBlank() }.joinToString(" · ")
}

@Composable
fun SamaTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("tvsama_settings", 0) }
    var oled by remember { mutableStateOf(preferences.getBoolean("oled", false)) }
    var motion by remember { mutableStateOf(preferences.getBoolean("motion", true)) }
    var compact by remember { mutableStateOf(preferences.getString("density", "Confort") == "Compact") }
    DisposableEffect(preferences) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == "oled") oled = preferences.getBoolean("oled", false)
            if (key == null || key == "motion") motion = preferences.getBoolean("motion", true)
            if (key == null || key == "density") compact = preferences.getString("density", "Confort") == "Compact"
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    CompositionLocalProvider(ThemeBackground provides if (oled) Color.Black else Color(0xFF141414),
        MotionEnabled provides motion, CompactPosters provides compact) {
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
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val motion = MotionEnabled.current
    val white = focused || pressed || primary
    val background by animateColorAsState(
        if (primary && !focused) Accent else if (white && enabled) Color.White else Color.Transparent,
        animationSpec = if (motion) tween(150) else snap(), label = "actionBackground")
    val foreground by animateColorAsState(if (!enabled) Muted.copy(alpha = .45f) else if (white) Color.Black else Color.White,
        animationSpec = if (motion) tween(150) else snap(), label = "actionForeground")
    
    val scale by animateFloatAsState(if (focused && motion) 1.03f else 1f, 
        animationSpec = if (motion) tween(150) else snap(), label = "actionScale")

    Box(modifier
        .graphicsLayer { 
            scaleX = scale
            scaleY = scale 
        }
        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
        .onFocusChanged { focused = it.isFocused }
        .clip(RoundedCornerShape(6.dp))
        .background(background)
        .border(if (focused) 3.dp else if (selected || primary) 2.dp else 1.dp,
            if (enabled && focused && primary) Accent else if (enabled && focused) Color.White else if (enabled && primary) Accent else if (enabled && selected) Color.White else Line, RoundedCornerShape(6.dp))
        .clickable(interactionSource = interaction, indication = LocalIndication.current, enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 18.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text(label, color = foreground,
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun IconAction(@androidx.annotation.DrawableRes icon: Int, description: String,
               modifier: Modifier = Modifier, tint: Color = Color.White,
               iconSize: Dp = 24.dp, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val motion = MotionEnabled.current
    val scale by animateFloatAsState(if (focused && motion) 1.06f else 1f,
        animationSpec = if (motion) tween(150) else snap(), label = "iconActionScale")
    Box(modifier
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
        .onFocusChanged { focused = it.isFocused }
        .clip(RoundedCornerShape(8.dp))
        .background(if (focused) Color(0xFF454545) else Color(0xFF171717))
        .border(if (focused) 3.dp else 1.dp, if (focused) Color.White else Line, RoundedCornerShape(8.dp))
        .semantics { contentDescription = description }
        .clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun Artwork(url: String, title: String, modifier: Modifier = Modifier, crop: Boolean = true, isBanner: Boolean = false) {
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFF151515)), contentAlignment = Alignment.Center) {
        Text(title.take(1).uppercase(), color = Line, fontSize = 56.sp, fontWeight = FontWeight.Bold)
        if (url.isNotBlank()) AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
            ImageView(context).apply {
                scaleType = if (crop) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
                contentDescription = title
                clipToOutline = true
            }
        }, update = { view ->
            view.contentDescription = title
            view.scaleType = if (crop) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
            view.clipToOutline = true
            // Low-RAM tuning: cap the decoded bitmap to smaller dimensions, prefer RGB_565 (half the memory of ARGB_8888)
            // to significantly reduce heap usage, and use disk cache to avoid repeat network downloads.
            // Banners (16:10) are decoded at 320×180; posters (2:3) at 240×360 — saves ~35 % heap vs a single
            // 320×480 budget for all artwork types. Memory cache stays on so scrolling back is instant.
            val (decodeW, decodeH) = if (isBanner) 320 to 180 else 240 to 360
            val requestOptions = com.bumptech.glide.request.RequestOptions()
                .override(decodeW, decodeH)
                .format(DecodeFormat.PREFER_RGB_565)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .skipMemoryCache(false)
                .error(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

            Glide.with(view)
                .load(com.streamflixreborn.streamflix.utils.SourceAddresses.rewrite(url))
                .apply(requestOptions)
                .thumbnail(0.1f)
                .into(view)
        }, onRelease = { Glide.with(it).clear(it) })
    }
}

@Composable
fun PosterCard(anime: Anime, modifier: Modifier = Modifier, query: String = "", selected: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val motion = MotionEnabled.current
    val currentLanguage = LocalTitleLanguage.current
    val shown = remember(anime.id, anime.provider, currentLanguage) { anime.displayTitle(currentLanguage) }
    val highlightedTitle = remember(shown, query) { SearchHighlighting.highlight(shown, query) }
    val isPlayable = anime.provider.isNotBlank() && (managerIsProviderEnabled(anime.provider) || anime.references.any { managerIsProviderEnabled(it.provider) })

    val scale by animateFloatAsState(if ((focused || selected) && motion) 1.04f else 1f,
        animationSpec = if (motion) spring(dampingRatio = 0.75f, stiffness = 350f) else snap(), label = "posterFocusScale")
    val borderColor by animateColorAsState(if (focused || selected) Accent else Color.Transparent,
        animationSpec = if (motion) tween(150) else snap(), label = "posterBorderColor")

    Column(modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .defaultMinSize(minHeight = 48.dp)
        .onFocusChanged { focused = it.isFocused }
        .clip(RoundedCornerShape(8.dp))
        .border(if (focused || selected) 3.dp else 0.dp, borderColor, RoundedCornerShape(8.dp))
        .clickable(onClick = onClick)
        .padding(6.dp)) {
        Box {
            Artwork(anime.poster, shown, Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(6.dp)))
            if (!isPlayable) {
                Box(Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.Black.copy(alpha = .5f)).clip(RoundedCornerShape(4.dp))) {
                    Text("Indisponible", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
            val badgeColor = when (anime.tag) {
                "Film" -> Coral
                "Série" -> Aqua
                "Animation" -> Violet
                else -> Color.Transparent
            }
            if (badgeColor != Color.Transparent) {
                Box(Modifier.align(Alignment.TopStart).padding(4.dp).background(badgeColor).clip(RoundedCornerShape(4.dp))) {
                    Text(anime.tag.take(1), color = if (anime.tag == "Film") Color.Black else Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
        Text(highlightedTitle, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.dp).heightIn(min = 40.dp))
        Text(listOfNotNull(CatalogIdentity.effectiveYear(anime)?.toString()).joinToString(" · "), color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Shared provider-enabled check so Ui.kt does not depend on StreamFlixProviderManager internals directly. */
internal fun managerIsProviderEnabled(provider: String): Boolean =
    StreamFlixProviderManager.getInstance().isProviderEnabled(provider)

@Composable
fun ResumeCard(entry: SavedPlayback, modifier: Modifier = Modifier.width(230.dp), onRemove: () -> Unit, onClick: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val currentLanguage = LocalTitleLanguage.current
    val shown = remember(entry.anime.id, entry.anime.provider, currentLanguage) { entry.anime.displayTitle(currentLanguage) }
    if (menu) AlertDialog(onDismissRequest = { menu = false }, title = { Text(shown) },
        text = { Text("Retirer ce titre de Reprendre ? La progression des épisodes sera conservée.") },
        confirmButton = { TextButton(onClick = { onRemove(); menu = false }) { Text("Retirer") } },
        dismissButton = { TextButton(onClick = { menu = false }) { Text("Annuler") } })
    var focused by remember { mutableStateOf(false) }
    val motion = MotionEnabled.current
    val scale by animateFloatAsState(if (focused && motion) 1.03f else 1f,
        animationSpec = if (motion) tween(150) else snap(), label = "resumeCardScale")
    Column(modifier.graphicsLayer { scaleX = scale; scaleY = scale }.defaultMinSize(minHeight = 48.dp)
        .onFocusChanged { focused = it.isFocused }.clip(RoundedCornerShape(8.dp))
        .border(if (focused) 3.dp else 1.dp, if (focused) Color.White else Line, RoundedCornerShape(8.dp))
        .combinedClickable(onClick = onClick, onLongClick = { menu = true }).padding(8.dp)) {
        Artwork(entry.anime.poster, shown, Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(6.dp)), isBanner = true)
        Text(shown, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
        Text(if (entry.anime.tag == "Film") "Film" else if (entry.season > 0) "S${entry.season} · E${entry.number} — ${entry.episodeTitle}" else entry.episodeTitle, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        androidx.compose.material3.LinearProgressIndicator(progress = { (entry.position.toFloat() / entry.duration.coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), color = Accent, trackColor = Line)
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String = "") {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.width(4.dp).height(24.dp).clip(RoundedCornerShape(2.dp)).background(Accent))
        Text(title, style = MaterialTheme.typography.headlineLarge, color = Color.White)
    }
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
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
        items(CATEGORY_ITEMS) { (label, color) ->
            var focused by remember { mutableStateOf(false) }
            Box(Modifier.widthIn(min = 112.dp).onFocusChanged { focused = it.isFocused }
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
    BoxWithConstraints(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = .10f)).border(1.dp, color.copy(alpha = .45f), RoundedCornerShape(8.dp)).padding(16.dp)) {
        if (maxWidth < 600.dp) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, color = Muted, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), content = content)
        } else Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(title, fontWeight = FontWeight.SemiBold); Text(description, color = Muted, fontSize = 12.sp) }
            Row(Modifier.widthIn(max = 460.dp).horizontalScroll(rememberScrollState()), content = content)
        }
    }
}

@Composable
fun LoadingDialog(message: String, onClose: () -> Unit) {
    val closeFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; closeFocus.requestFocus() }
    AlertDialog(onDismissRequest = {},
        properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text("Chargement en cours") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent)
            Text(message)
        } },
        confirmButton = { TextButton(onClick = onClose, modifier = Modifier.focusRequester(closeFocus)) { Text("Fermer") } })
}

@Composable
fun TvSettings(language: String, onLanguage: (String) -> Unit, autoplay: Boolean, onAutoplay: (Boolean) -> Unit,
    hasHistoryBackup: Boolean, onClearHistory: () -> Unit, onRestoreHistory: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("tvsama_settings", Context.MODE_PRIVATE) }
    val pairedDevice = prefs.getString("paired_tv_device", null)
    var startup by remember { mutableStateOf(prefs.getString("startup", "Accueil") ?: "Accueil") }
    var density by remember { mutableStateOf(prefs.getString("density", "Confort") ?: "Confort") }
    var motion by remember { mutableStateOf(prefs.getBoolean("motion", true)) }
    var subtitles by remember { mutableStateOf(prefs.getBoolean("subtitles", true)) }
    val timer = remember { SleepTimer(context) }
    var sleepMinutes by remember { mutableIntStateOf(timer.minutes()) }
    var showUnavailable by remember { mutableStateOf(prefs.getBoolean("show_unavailable_startup", true)) }
    var autoDim by remember { mutableStateOf(prefs.getBoolean("pause_dimming", true)) }
    var audioBoost by remember { mutableStateOf(prefs.getBoolean("audio_boost", true)) }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmClearHistory by remember { mutableStateOf(false) }
    var historyMessage by remember { mutableStateOf("") }
    fun restoreHistory() {
        historyMessage = runCatching { onRestoreHistory(); "Historique restauré." }
            .getOrElse { "Restauration impossible : ${it.localizedMessage.orEmpty()}" }
    }
    if (confirmClearHistory) AlertDialog(
        onDismissRequest = { confirmClearHistory = false },
        title = { Text("Effacer l’historique ?") },
        text = { Text("La progression de lecture sera supprimée. Vous pourrez restaurer cette copie depuis les réglages jusqu’au prochain effacement.") },
        confirmButton = { Button(onClick = {
            historyMessage = runCatching { onClearHistory(); "Historique effacé." }
                .getOrElse { "Effacement impossible : ${it.localizedMessage.orEmpty()}" }
            confirmClearHistory = false
        }) { Text("Effacer") } },
        dismissButton = { TextButton(onClick = { confirmClearHistory = false }) { Text("Annuler") } }
    )
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("Réinitialiser les préférences ?") },
        text = { Text("Les réglages de l’interface, des sources et l’association à la TV seront réinitialisés. Votre historique et vos favoris seront conservés.") },
        confirmButton = {
            Button(onClick = {
                confirmReset = false
                scope.launch {
                    try {
                        RemoteLink.resetPreferences(context)
                        timer.configure(0)
                        startup = "Accueil"; density = "Confort"; motion = true; subtitles = true
                        sleepMinutes = 0; showUnavailable = true; autoDim = true; audioBoost = true
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (e: Exception) { historyMessage = e.message.orEmpty() }
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E), contentColor = Color.White)) {
                Text("Réinitialiser")
            }
        },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Annuler") } }
    )
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle("Réglages TV", "Personnalisez votre expérience TvSama depuis le canapé.")
        Text("Lecture", style = MaterialTheme.typography.titleLarge)
        PreferenceCard("Version préférée", "Sélection par défaut pour vos films et séries : VF ou VOSTFR.", Accent) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("VF", "VOSTFR").forEach { version ->
                    Action(version, selected = language == version) { onLanguage(version) }
                }
            }
        }
        PreferenceCard("Minuterie automatique", "Met la lecture en pause après la durée choisie.", Gold) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (listOf(0) + SleepTimer.options).forEach { minutes ->
                    Action(if (minutes == 0) "Désactivée" else SleepTimer.label(minutes), selected = sleepMinutes == minutes) {
                        sleepMinutes = minutes; timer.configure(minutes)
                    }
                }
            }
        }
        PreferenceCard("Épisode suivant", "Lance le suivant à l’outro détectée, après une éventuelle scène post-générique, ou à la fin si aucun segment n’est connu.", Accent) {
            Action(if (autoplay) "Activé" else "Manuel", selected = autoplay) { onAutoplay(!autoplay) }
        }
        PreferenceCard("Assombrissement automatique", "Assombrit fortement l’écran après 30 secondes en pause. Un toucher, un mouvement ou une touche rétablit l’image.", Gold) {
            Action(if (autoDim) "Activé" else "Désactivé", selected = autoDim) { autoDim = !autoDim; prefs.edit().putBoolean("pause_dimming", autoDim).apply() }
        }
        PreferenceCard("Amplification du son", "Monte fortement les pistes faibles (+15 dB) et compresse les pics pour garder les dialogues réguliers.", Accent) {
            Action(if (audioBoost) "Activée" else "Désactivée", selected = audioBoost) {
                audioBoost = !audioBoost; prefs.edit().putBoolean("audio_boost", audioBoost).apply()
            }
        }
        PreferenceCard("Sous-titres français", "Active la piste française par défaut en VOSTFR.", Violet) {
            Action(if (subtitles) "Activés" else "Désactivés", selected = subtitles) { subtitles = !subtitles; prefs.edit().putBoolean("subtitles", subtitles).apply() }
        }
        PreferenceCard("Voir les sources indisponibles au démarrage", "Affiche un avertissement si une source est signalée indisponible. Les sources restent accessibles dans le menu Sources.", Gold) {
            Action(if (showUnavailable) "Activé" else "Désactivé", selected = showUnavailable) {
                showUnavailable = !showUnavailable
                prefs.edit().putBoolean("show_unavailable_startup", showUnavailable).apply()
            }
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
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) { CastRouteButton(Modifier.size(52.dp)); Text("Le bouton télécommande ouvre l’association QR.", modifier = Modifier.weight(1f), color = Muted, fontSize = 12.sp) }
        if (!pairedDevice.isNullOrBlank()) Text("Téléviseur associé : $pairedDevice", color = Accent, fontSize = 13.sp)
        HorizontalDivider(color = Line)
        Text("Bibliothèque", style = MaterialTheme.typography.titleLarge)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth < 600.dp) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Action("Effacer l’historique", Modifier.fillMaxWidth()) { confirmClearHistory = true }
                if (hasHistoryBackup) Action("Restaurer l’historique", Modifier.fillMaxWidth()) { restoreHistory() }
                Action("Réinitialiser les préférences", Modifier.fillMaxWidth()) { confirmReset = true }
            } else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Action("Effacer l’historique") { confirmClearHistory = true }
                if (hasHistoryBackup) Action("Restaurer l’historique") { restoreHistory() }
                Action("Réinitialiser les préférences") { confirmReset = true }
            }
        }
        if (historyMessage.isNotBlank()) Text(historyMessage, color = Muted)
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
