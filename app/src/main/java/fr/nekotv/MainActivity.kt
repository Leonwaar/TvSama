package fr.nekotv

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.content.pm.ActivityInfo
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.data?.takeIf { it.scheme == "tvsama" && it.host == "pair" }?.let { pairing ->
            getSharedPreferences("tvsama_settings", MODE_PRIVATE).edit()
                .putString("paired_tv_token", pairing.getQueryParameter("token").orEmpty())
                .putString("paired_tv_device", pairing.getQueryParameter("device").orEmpty())
                .apply()
        }
        setContent { SamaTheme { TvSamaApp() } }
    }
}

private fun setPlayerFullscreen(activity: Activity?, fullscreen: Boolean) {
    if (activity == null) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        activity.window.insetsController?.let { controller ->
            if (fullscreen) controller.hide(WindowInsets.Type.systemBars()) else controller.show(WindowInsets.Type.systemBars())
        }
    } else {
        @Suppress("DEPRECATION")
        activity.window.decorView.systemUiVisibility = if (fullscreen) {
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        } else 0
    }
    if (!activity.isTelevision()) activity.requestedOrientation = if (fullscreen) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
}

private enum class Page(val label: String) { HOME("Accueil"), SEARCH("Recherche"), FAVORITES("Ma liste"), HISTORY("Reprendre"), SOURCES("Sources"), SETTINGS("Réglages"), DETAIL("Fiche"), PLAYER("Lecture") }

@Composable
private fun TvSamaApp() {
    val context = LocalContext.current
    val manager = remember { StreamFlixProviderManager.getInstance() }
    val library = remember { LibraryStore(context) }
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf(Page.HOME) }
    var returnPage by remember { mutableStateOf(Page.HOME) }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("Tous") }
    var language by rememberSaveable { mutableStateOf(library.language()) }
    var catalog by remember { mutableStateOf<List<Anime>>(emptyList()) }
    var catalogLoading by remember { mutableStateOf(false) }
    var catalogError by remember { mutableStateOf("") }
    var generation by remember { mutableIntStateOf(0) }
    var catalogPage by remember { mutableIntStateOf(1) }
    var loadingMore by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Anime?>(null) }
    var episode by remember { mutableStateOf<Episode?>(null) }
    var source by remember { mutableStateOf<VideoSource?>(null) }
    var sources by remember { mutableStateOf<List<VideoSource>>(emptyList()) }
    var detailLoading by remember { mutableStateOf(false) }
    var streamLoading by remember { mutableStateOf(false) }
    var detailError by remember { mutableStateOf("") }
    var playerError by remember { mutableStateOf("") }
    var favorites by remember { mutableStateOf(library.favorites()) }
    var history by remember { mutableStateOf(library.history()) }
    var autoplay by remember { mutableStateOf(library.autoplay()) }
    var subtitlesEnabled by remember { mutableStateOf(context.getSharedPreferences("tvsama_settings", 0).getBoolean("subtitles", true)) }
    var currentQuality by remember { mutableStateOf("Détection…") }
    var isFullscreen by remember { mutableStateOf(false) }
    val hostActivity = context as? Activity
    var providerNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var providerRevision by remember { mutableIntStateOf(0) }
    var resolveJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var pageJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var detailJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun navigate(destination: Page) {
        detailJob?.cancel(); resolveJob?.cancel(); pageJob?.cancel()
        detailLoading = false; streamLoading = false; loadingMore = false
        if (destination == Page.HOME) query = ""
        page = destination; favorites = library.favorites(); history = library.history()
    }

    fun openDetails(anime: Anime, requestedEpisode: String? = null) {
        if (anime.tag == "Flux personnel") {
            selected = anime; episode = null; source = VideoSource(anime.title, anime.id, "VF", "Auto", "Personnel"); sources = listOf(source!!); page = Page.PLAYER; return
        }
        detailJob?.cancel(); resolveJob?.cancel()
        if (page != Page.DETAIL && page != Page.PLAYER) returnPage = page
        selected = anime; episode = null; sources = emptyList(); source = null; detailError = ""; playerError = ""
        page = Page.DETAIL; detailLoading = true; streamLoading = false
        detailJob = scope.launch {
            try {
                val loaded = manager.loadDetails(anime)
                selected = loaded
                episode = loaded.episodes.firstOrNull { it.id == requestedEpisode } ?: loaded.episodes.firstOrNull()
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { detailError = "Impossible de charger cette fiche. ${e.message.orEmpty()}"
            } finally { detailLoading = false }
        }
    }

    fun resolve(play: Boolean, refresh: Boolean = false, target: Episode? = episode) {
        val anime = selected ?: return
        resolveJob?.cancel(); episode = target; streamLoading = true; sources = emptyList(); detailError = ""; playerError = ""
        resolveJob = scope.launch {
            try {
                sources = manager.resolveSources(anime, target, language, refresh)
                if (sources.isEmpty()) detailError = "Aucun serveur disponible en ${if (language == "Toutes") "VF ou VOSTFR" else language}. Actualisez les sources ou choisissez un autre épisode."
                else if (play) { source = sources.first(); currentQuality = "Détection…"; page = Page.PLAYER }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { detailError = "La source ne répond pas. ${e.message.orEmpty()}"
            } finally { streamLoading = false }
        }
    }

    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { query = it; page = Page.SEARCH }
    }
    LaunchedEffect(Unit) {
        providerNames = manager.getProviderNames()
        scope.launch {
            manager.refreshSources()
            Toast.makeText(context, "Adresse actualisée", Toast.LENGTH_SHORT).show()
            providerRevision++
            generation++
        }
    }
    LaunchedEffect(query, category, generation) {
        pageJob?.cancel(); loadingMore = false
        catalogLoading = true; catalogError = ""; catalogPage = 1
        try {
            if (query.isNotBlank()) delay(450)
            catalog = manager.searchAllProviders(query, category)
            if (catalog.isEmpty()) catalogError = "Aucun résultat disponible. Vérifiez les fournisseurs activés ou essayez une autre recherche."
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { catalog = emptyList(); catalogError = "Catalogue indisponible. Vérifiez votre connexion puis réessayez."
        } finally { catalogLoading = false }
    }
    BackHandler(page != Page.HOME || isFullscreen) {
        if (isFullscreen) {
            isFullscreen = false
            setPlayerFullscreen(hostActivity, false)
        } else when (page) {
            Page.PLAYER -> { page = Page.DETAIL; history = library.history() }
            Page.DETAIL -> { detailJob?.cancel(); resolveJob?.cancel(); page = returnPage }
            else -> navigate(Page.HOME)
        }
    }

    Surface(Modifier.fillMaxSize(), color = Ink) {
        if (page == Page.PLAYER && source != null) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                if (!isFullscreen) Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Action("‹ Retour") { page = Page.DETAIL; history = library.history() }
                    Column(Modifier.weight(1f)) {
                        Text(selected?.title.orEmpty(), fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(listOfNotNull(episode?.title, source?.language, source?.quality, "actuel $currentQuality", source?.latencyMs?.takeIf { it > 0 }?.let { "${it} ms" }).joinToString(" · "), color = Muted, fontSize = 12.sp, maxLines = 1)
                    }
                    Action("CC ${if (subtitlesEnabled) "Activés" else "Désactivés"}") { subtitlesEnabled = !subtitlesEnabled; context.getSharedPreferences("tvsama_settings", 0).edit().putBoolean("subtitles", subtitlesEnabled).apply() }
                    Action("☷ Serveurs") { page = Page.DETAIL }
                    CastRouteButton(Modifier.size(48.dp))
                }
                if (!isFullscreen && sources.size > 1) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                    items(sources) { candidate ->
                        Action("${candidate.provider} · ${candidate.quality} · ${candidate.latencyMs.takeIf { it > 0 } ?: "?"} ms", selected = candidate.url == source?.url) {
                            source = candidate; currentQuality = "Détection…"; playerError = ""
                        }
                    }
                }
                if (!isFullscreen && playerError.isNotBlank()) {
                    Text(playerError, color = Color(0xFFFFB4AB), modifier = Modifier.padding(12.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        sources.filter { it.url != source?.url }.forEach { alternative -> Action("${alternative.provider} · ${alternative.name}") { source = alternative; currentQuality = "Détection…"; playerError = "" } }
                        Action("Actualiser") { page = Page.DETAIL; resolve(false, true) }
                    }
                }
                val playingAnime = selected
                val playingEpisode = episode
                TvSamaPlayer(source = source!!, title = "${playingAnime?.title.orEmpty()}${playingEpisode?.title?.let { " · $it" }.orEmpty()}", poster = playingAnime?.poster.orEmpty(),
                    resumeAt = playingAnime?.let { library.progress(it, playingEpisode) } ?: 0,
                    subtitlesEnabled = subtitlesEnabled,
                    onActualQuality = { currentQuality = it },
                    isFullscreen = isFullscreen,
                    onToggleFullscreen = {
                        isFullscreen = !isFullscreen
                        setPlayerFullscreen(hostActivity, isFullscreen)
                    },
                    onProgress = { position, duration -> playingAnime?.let { library.save(it, playingEpisode, position, duration) } },
                    modifier = Modifier.weight(1f).fillMaxWidth(), onError = { message ->
                        playerError = message
                        val failed = source?.url
                        playingAnime?.let { animeForRetry -> scope.launch {
                            val refreshed = runCatching { manager.resolveSources(animeForRetry, playingEpisode, language, refresh = true) }.getOrDefault(emptyList())
                            val alternatives = refreshed.filter { it.url != failed }
                            if (alternatives.isNotEmpty()) { sources = refreshed; source = alternatives.first(); currentQuality = "Détection…"; playerError = "" }
                        } }
                    },
                    onEnded = {
                        val entries = selected?.episodes.orEmpty()
                        val index = entries.indexOfFirst { it.id == episode?.id }
                        if (autoplay && index >= 0 && index + 1 < entries.size) {
                            isFullscreen = false
                            setPlayerFullscreen(hostActivity, false)
                            page = Page.DETAIL
                            resolve(true, target = entries[index + 1])
                        }
                    })
            }
        } else BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 840.dp
            Row(Modifier.fillMaxSize()) {
                if (wide) Column(Modifier.width(190.dp).fillMaxHeight().padding(start = 24.dp, end = 18.dp, top = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Brand()
                    Spacer(Modifier.height(26.dp))
                    Page.entries.filter { it != Page.DETAIL && it != Page.PLAYER && it != Page.SEARCH }.forEach { destination ->
                        Action(destination.label, Modifier.fillMaxWidth(), selected = page == destination) {
                            navigate(destination)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text("LE CINÉMA, CHEZ VOUS.", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 24.dp))
                }
                Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = if (wide) 24.dp else 16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!wide) Brand() else Text("COLLECTION FRANÇAISE", color = Muted, fontSize = 11.sp, letterSpacing = 2.sp)
                        Spacer(Modifier.weight(1f))
                        Text("Leon Made <3", color = Color(0xFFFF79B9), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Spacer(Modifier.width(12.dp)); Action("⌕", Modifier.size(44.dp).padding(0.dp)) { navigate(Page.SEARCH) }
                        Spacer(Modifier.width(8.dp)); CastRouteButton(Modifier.size(44.dp))
                    }
                    if (!wide) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                        items(Page.entries.filter { it != Page.DETAIL && it != Page.PLAYER && it != Page.SEARCH }) { destination ->
                            Action(destination.label, selected = page == destination) { navigate(destination) }
                        }
                    }
                    when (page) {
                        Page.HOME, Page.SEARCH -> {
                            if (page == Page.SEARCH) {
                                SectionTitle("Qu’allez-vous regarder ?", "Une recherche dans vos catalogues français.")
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(query, { query = it }, Modifier.weight(1f), placeholder = { Text("Film, série, animation…") }, singleLine = true)
                                    Action("Micro") {
                                        try { voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")) }
                                        catch (_: Exception) { catalogError = "La recherche vocale n’est pas disponible sur cet appareil." }
                                    }
                                }
                            }
                            CategoryRail(category) { category = it }
                            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.End) { Action("↻ Actualiser") { scope.launch { manager.refreshSources(); generation++ } } }
                            if (catalogLoading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color.White)
                            if (catalogError.isNotBlank()) Text(catalogError, color = Muted, modifier = Modifier.padding(vertical = 14.dp))
                            LazyVerticalGrid(columns = GridCells.Adaptive(if (wide) 145.dp else 125.dp), modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
                                if (page == Page.HOME && catalog.isNotEmpty()) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                    Hero(catalog.first(), wide) { openDetails(catalog.first()) }
                                }
                                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                    Text(if (page == Page.SEARCH) "${catalog.size} titres" else "À découvrir", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
                                }
                                items(catalog) { anime -> PosterCard(anime) { openDetails(anime) } }
                                if (catalog.isNotEmpty()) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                    Action(if (loadingMore) "Chargement…" else "Afficher davantage", enabled = !loadingMore) {
                                        pageJob = scope.launch {
                                            loadingMore = true
                                            try {
                                                val next = manager.searchAllProviders(query, category, catalogPage + 1)
                                                if (next.isEmpty()) catalogError = "Vous avez atteint la fin des résultats disponibles."
                                                else { catalog = (catalog + next).distinctBy { it.title.lowercase() + it.year + it.tag }; catalogPage++ }
                                            } catch (e: CancellationException) { throw e } catch (_: Exception) { catalogError = "Impossible de charger la suite." } finally { loadingMore = false }
                                        }
                                    }
                                }
                            }
                        }
                        Page.DETAIL -> selected?.let { anime ->
                            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
                                item { Action("‹ ${returnPage.label}") { page = returnPage; detailJob?.cancel(); resolveJob?.cancel() } }
                                item {
                                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                        if (wide) Artwork(anime.poster, anime.title, Modifier.width(175.dp).height(260.dp))
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Text(anime.tag.uppercase(), color = Accent, fontSize = 12.sp, letterSpacing = 2.sp)
                                            SectionTitle(anime.title)
                                            Text((listOfNotNull(anime.year?.toString()) + anime.genres).joinToString(" · "), color = Muted)
                                            Text(anime.description.ifBlank { "Le synopsis n’est pas disponible pour ce titre." }, color = Color(0xFFE0E0E0))
                                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Action(if (streamLoading) "Recherche des serveurs…" else if (library.progress(anime, episode) > 0) "▶ Reprendre" else "▶ Regarder", primary = true, enabled = !detailLoading && !streamLoading) { resolve(true) }
                                                Action(if (library.favorite(anime)) "✓ Dans ma liste" else "+ Ma liste") { library.toggle(anime); favorites = library.favorites() }
                                            }
                                        }
                                    }
                                }
                                if (detailLoading || streamLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color.White) }
                                if (detailError.isNotBlank()) item { Text(detailError, color = Color(0xFFFFB4AB)); Action("Réessayer") { if (detailLoading || anime.episodes.isEmpty() && anime.tag != "Film") openDetails(anime) else resolve(false, true) } }
                                if (anime.episodes.isNotEmpty()) item {
                                    EpisodePicker(anime.episodes, episode, language, onSelect = { chosen -> episode = chosen; sources = emptyList(); detailError = ""; resolve(true, target = chosen) })
                                }
                                item {
                                    Text("Versions et serveurs", style = MaterialTheme.typography.titleLarge)
                                    Text("Les serveurs disponibles sont classés par qualité. Vous gardez le choix.", color = Muted, modifier = Modifier.padding(vertical = 8.dp))
                                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf("Toutes", "VF", "VOSTFR").forEach { filter -> Action(if (filter == "Toutes") "VF + VOSTFR" else filter, selected = language == filter) { language = filter; library.setLanguage(filter); episode = anime.episodes.firstOrNull { filter == "Toutes" || it.language == filter }; sources = emptyList(); resolveJob?.cancel(); streamLoading = false } }
                                        Action("↻ Sources", enabled = !streamLoading && !detailLoading) { resolve(false, true) }
                                    }
                                }
                                items(sources) { video ->
                                    Action("▶ ${video.provider}  ·  ${video.name}  ·  ${video.language}  ${video.quality}  ·  ${video.latencyMs.takeIf { it > 0 } ?: "?"} ms", Modifier.fillMaxWidth()) { source = video; currentQuality = "Détection…"; playerError = ""; page = Page.PLAYER }
                                }
                            }
                        }
                        Page.FAVORITES -> {
                            SectionTitle("Ma liste", "Vos films et séries, à retrouver quand vous voulez.")
                            if (favorites.isEmpty()) EmptyState("Votre collection commence ici", "Ouvrez une fiche puis sélectionnez « + Ma liste ».")
                            LazyVerticalGrid(GridCells.Adaptive(145.dp), Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) { items(favorites) { anime -> PosterCard(anime) { openDetails(anime) } } }
                        }
                        Page.HISTORY -> {
                            SectionTitle("Reprendre", "Retrouvez votre épisode et votre position de lecture.")
                            if (history.isEmpty()) EmptyState("Rien en cours pour le moment", "Vos lectures apparaîtront ici automatiquement.")
                            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                items(history) { entry ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Artwork(entry.anime.poster, entry.anime.title, Modifier.width(70.dp).height(100.dp))
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(entry.anime.title, fontWeight = FontWeight.Bold)
                                            Text("${entry.episodeTitle} · ${entry.position / 60000} min", color = Muted)
                                            LinearProgressIndicator(progress = { (entry.position.toFloat() / entry.duration.coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = Color.White)
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Action("Reprendre") { openDetails(entry.anime, entry.episodeId) }
                                                Action("×") { library.removeHistory(entry.anime, entry.episodeId); history = library.history() }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Page.SOURCES -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            SectionTitle("Vos sources", "Catalogues français regroupés dans une seule recherche.")
                            Action("↻ Actualiser les catalogues") { scope.launch { manager.refreshSources(); generation++ }; providerRevision++ }
                            providerNames.forEach { name ->
                                val enabled = remember(name, providerRevision) { manager.isProviderEnabled(name) }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Column(Modifier.weight(1f)) {
                                        Text(name, fontWeight = FontWeight.SemiBold)
                                        Text(manager.externalSource(name)?.url ?: (manager.providerStatuses()[name] ?: "Prêt à rechercher"), color = Muted, fontSize = 12.sp, maxLines = 1)
                                        manager.externalSource(name)?.let { Text(manager.providerStatuses()[name] ?: "Catalogue web", color = Muted, fontSize = 11.sp) }
                                    }
                                    Action(if (enabled) "Activé" else "Désactivé", selected = enabled) { manager.setProviderEnabled(name, !enabled); providerRevision++; generation++ }
                                }
                                HorizontalDivider(color = Line)
                            }
                            PersonalSources { custom -> selected = Anime(custom.name, "Flux personnel", emptyList(), id = custom.url); episode = null; source = custom; sources = listOf(custom); page = Page.PLAYER }
                            Spacer(Modifier.height(24.dp))
                        }
                        Page.SETTINGS -> TvSettings(language, { language = it; library.setLanguage(it) }, autoplay, { autoplay = it; library.setAutoplay(it) }, { library.clearHistory(); history = emptyList() })
                        else -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun Brand() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("TV", fontWeight = FontWeight.Black, fontSize = 25.sp, color = Color.White)
        Text("SAMA", fontWeight = FontWeight.Light, fontSize = 25.sp, letterSpacing = 2.sp, color = Color.White)
        Text("•", color = Accent, fontSize = 25.sp)
    }
}

@Composable
private fun Hero(anime: Anime, wide: Boolean, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(if (wide) 310.dp else 280.dp)) {
        Artwork(anime.banner.ifBlank { anime.poster }, anime.title, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Ink, Ink.copy(alpha = .7f), Color.Transparent))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Ink))))
        Column(Modifier.align(Alignment.BottomStart).widthIn(max = 500.dp).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("À L’AFFICHE", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Text(anime.title, color = Color.White, fontSize = if (wide) 36.sp else 28.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            Text(listOfNotNull(anime.tag, anime.year?.toString()).joinToString(" · "), color = Muted)
            Action("Découvrir  →", primary = true, onClick = onClick)
        }
    }
}

@Composable
private fun EpisodePicker(episodes: List<Episode>, selected: Episode?, language: String, onSelect: (Episode) -> Unit) {
    val visibleEpisodes = episodes.filter { language == "Toutes" || it.language == language }
        .distinctBy { "${it.seasonNumber}|${it.number}|${it.language}|${it.title.trim().lowercase()}" }
    val seasons = visibleEpisodes.map { it.seasonNumber }.distinct().sorted()
    var season by remember(episodes, language) { mutableStateOf(selected?.seasonNumber ?: seasons.firstOrNull()) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Épisodes", style = MaterialTheme.typography.titleLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(seasons) { number -> Action("Saison $number", selected = season == number) { season = number } } }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(visibleEpisodes.filter { it.seasonNumber == season }) { entry ->
                Column(Modifier.width(225.dp)) {
                    if (entry.poster.isNotBlank()) Artwork(entry.poster, entry.title, Modifier.fillMaxWidth().height(120.dp))
                    val label = if (language == "Toutes") "▶ ${entry.title} · ${entry.language}" else "▶ ${entry.title}"
                    Action(label, Modifier.fillMaxWidth(), selected = selected?.id == entry.id) { onSelect(entry) }
                }
            }
        }
    }
}

@Composable
private fun PersonalSources(onPlay: (VideoSource) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("sources", 0) }
    var entries by remember { mutableStateOf(prefs.getString("items", "").orEmpty().lines().filter { it.contains("|") }) }
    var showAdd by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    Text("Flux personnels", style = MaterialTheme.typography.titleLarge)
    Action("+ Ajouter un lien vidéo") { showAdd = true }
    entries.forEach { line ->
        val parts = line.split("|", limit = 2)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Action("▶ ${parts[0]}", Modifier.weight(1f)) { onPlay(VideoSource(parts[0], parts[1], "VF", "Auto", "Personnel")) }
            Action("Retirer") { entries = entries - line; prefs.edit().putString("items", entries.joinToString("\n")).apply() }
        }
    }
    if (showAdd) AlertDialog(onDismissRequest = { showAdd = false }, title = { Text("Ajouter un flux") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Nom") }, singleLine = true)
            OutlinedTextField(url, { url = it }, label = { Text("Lien HTTPS du média") }, singleLine = true)
            if (error.isNotBlank()) Text(error)
        }
    }, confirmButton = { TextButton(onClick = {
        val parsed = android.net.Uri.parse(url.trim())
        if (name.isBlank() || name.contains("|") || name.contains("\n") || parsed.scheme != "https" || parsed.host.isNullOrBlank() || url.contains("\n")) error = "Indiquez un nom et une adresse HTTPS valide."
        else { entries = entries + "${name.trim()}|${url.trim()}"; prefs.edit().putString("items", entries.joinToString("\n")).apply(); showAdd = false; name = ""; url = ""; error = "" }
    }) { Text("Ajouter") } }, dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Annuler") } })
}
