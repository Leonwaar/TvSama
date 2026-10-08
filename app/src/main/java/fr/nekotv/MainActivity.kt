package fr.nekotv

import android.app.Activity
import android.content.Intent
import android.net.Uri
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
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.lifecycle.lifecycleScope

class MainActivity : FragmentActivity() {
    var lastInteraction by mutableLongStateOf(android.os.SystemClock.uptimeMillis())
        private set
    var wakePausedScreen: (() -> Boolean)? = null
    private fun recordInteraction() { lastInteraction = android.os.SystemClock.uptimeMillis() }
    fun notifyPlayerInteraction(): Boolean { recordInteraction(); return wakePausedScreen?.invoke() == true }
    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.actionMasked in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_MOVE)) {
            recordInteraction()
            if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN && wakePausedScreen?.invoke() == true) return true
        }
        return super.dispatchTouchEvent(event)
    }
    override fun dispatchGenericMotionEvent(event: android.view.MotionEvent): Boolean {
        recordInteraction()
        if (wakePausedScreen?.invoke() == true) return true
        return super.dispatchGenericMotionEvent(event)
    }
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action == android.view.KeyEvent.ACTION_DOWN && notifyPlayerInteraction()) return true
        return super.dispatchKeyEvent(event)
    }
    var inPictureInPicture by mutableStateOf(false)
        private set

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPictureInPicture = isInPictureInPictureMode
    }

    var pictureInPictureEligible = false
        set(value) {
            field = value
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                runCatching { setPictureInPictureParams(android.app.PictureInPictureParams.Builder()
                    .setAspectRatio(android.util.Rational(16, 9)).setAutoEnterEnabled(value).build()) }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.streamflixreborn.streamflix.utils.SourceWebSession.attach(this)
        RemoteLink.restore(this)
        handlePairing(intent)
        setContent { SamaTheme { TvSamaApp() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePairing(intent)
    }

    private fun handlePairing(intent: Intent?) {
        val uri = intent?.data?.takeIf { it.scheme == "tvsama" && it.host == "pair" } ?: return
        lifecycleScope.launch {
            try {
                RemoteLink.pair(this@MainActivity, uri.toString())
                Toast.makeText(this@MainActivity, "Télévision liée", Toast.LENGTH_LONG).show()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { Toast.makeText(this@MainActivity, "Association impossible : ${e.message}", Toast.LENGTH_LONG).show() }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!pictureInPictureEligible || Build.VERSION.SDK_INT < Build.VERSION_CODES.O || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S || !packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        val params = android.app.PictureInPictureParams.Builder()
            .setAspectRatio(android.util.Rational(16, 9))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) params.setAutoEnterEnabled(true)
        runCatching { enterPictureInPictureMode(params.build()) }
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

private enum class Page(val label: String) { HOME("Accueil"), SEARCH("Recherche"), FAVORITES("Ma liste"), HISTORY("Reprendre"), SOURCES("Sources"), LIVE("Directs sportifs"), SETTINGS("Réglages"), DETAIL("Fiche"), PLAYER("Lecture") }

@Composable
private fun TvSamaApp() {
    val context = LocalContext.current
    UpdatePrompt()
    val manager = remember { StreamFlixProviderManager.getInstance() }
    val library = remember { LibraryStore(context) }
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf(if ((context as? Activity)?.intent?.getBooleanExtra("open_live", false) == true) Page.LIVE else Page.HOME) }
    var returnPage by remember { mutableStateOf(Page.HOME) }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("Tous") }
    var language by rememberSaveable { mutableStateOf(library.language().takeIf { it in listOf("VF", "VOSTFR") } ?: "VF") }
    var catalog by remember { mutableStateOf<List<Anime>>(emptyList()) }
    var catalogKey by remember { mutableStateOf("Tous|") }
    val homeGridState = rememberLazyGridState()
    val searchGridState = rememberLazyGridState()
    val discoveryRowState = rememberLazyListState()
    val resumeRowState = rememberLazyListState()
    val catalogueFocus = remember { FocusRequester() }
    var lastHomeFocus by remember { mutableStateOf<String?>(null) }
    var lastSearchFocus by remember { mutableStateOf<String?>(null) }
    fun cardKey(anime: Anime) = "${anime.provider}|${anime.tag}|${anime.id}"
    fun cardModifier(anime: Anime): Modifier {
        val key = cardKey(anime)
        val previous = if (page == Page.HOME) lastHomeFocus else lastSearchFocus
        return (if (key == previous) Modifier.focusRequester(catalogueFocus) else Modifier)
            .onFocusChanged { if (it.isFocused) {
                if (page == Page.HOME) lastHomeFocus = key else if (page == Page.SEARCH) lastSearchFocus = key
            } }
    }
    LaunchedEffect(page) {
        val key = if (page == Page.HOME) lastHomeFocus else if (page == Page.SEARCH) lastSearchFocus else null
        if (context.isTelevision() && key != null && catalog.any { cardKey(it) == key }) {
            withFrameNanos { }
            runCatching { catalogueFocus.requestFocus() }
        }
    }
    val catalogUpdates = remember { kotlinx.coroutines.sync.Mutex() }
    suspend fun appendCatalogue(incoming: List<Anime>) = catalogUpdates.withLock {
        val existing = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { catalog }
        val updated = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { CatalogIdentity.appendStable(existing, incoming) }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { catalog = updated }
    }
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
    val failedLiveServers = remember(selected?.id) { mutableSetOf<String>() }
    val refreshedLiveServers = remember(selected?.id) { mutableSetOf<String>() }
    var livePlaybackRevision by remember(selected?.id) { mutableIntStateOf(0) }
    val failedVideos = remember(selected?.id, episode?.id, language) { mutableSetOf<String>() }
    var retryUsed by remember(selected?.id, episode?.id, language) { mutableStateOf(false) }
    var prefetchJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var favorites by remember { mutableStateOf(library.favorites()) }
    var history by remember { mutableStateOf(library.continuing()) }
    var autoplay by remember { mutableStateOf(library.autoplay()) }
    var subtitlesEnabled by remember { mutableStateOf(context.getSharedPreferences("tvsama_settings", 0).getBoolean("subtitles", true)) }
    var currentQuality by remember { mutableStateOf("Détection…") }
    var isFullscreen by remember { mutableStateOf(false) }
    var playerControlsVisible by remember { mutableStateOf(true) }
    val hostActivity = context as? Activity
    val inPip = (hostActivity as? MainActivity)?.inPictureInPicture == true
    var providerNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var providerRevision by remember { mutableIntStateOf(0) }
    var unavailableSources by remember { mutableStateOf<List<String>>(emptyList()) }
    var resolveJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var pageJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var recentSearches by remember { mutableStateOf(library.searches()) }
    var menuExpanded by remember { mutableStateOf(false) }
    var menuHadFocus by remember { mutableStateOf(false) }
    var detailJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var sourceAccess by remember { mutableStateOf<Pair<String, String>?>(null) }
    sourceAccess?.let { (name, url) -> SourceAccess(name, url, { sourceAccess = null }, { scope.launch { manager.refreshSources(); providerRevision++; generation++ } }) }

    fun refreshSources() {
        scope.launch {
            manager.refreshSources()
            unavailableSources = manager.sourceHealth().filterValues { it.startsWith("✕") }.keys.toList()
            providerRevision++
            generation++
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            val refreshed = try { manager.refreshDirectory(); providerRevision++; true }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { false }
            kotlinx.coroutines.delay(if (refreshed) 6 * 60 * 60 * 1000L else 30 * 60 * 1000L)
        }
    }

    fun navigate(destination: Page) {
        detailJob?.cancel(); resolveJob?.cancel(); pageJob?.cancel()
        detailLoading = false; streamLoading = false; loadingMore = false
        if (destination == Page.HOME) query = ""
        page = destination; favorites = library.favorites(); history = library.continuing()
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
                val loaded = manager.loadDetails(anime, onPartial = { partial ->
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        if (page == Page.DETAIL) {
                            val current = episode
                            selected = partial
                            episode = partial.episodes.firstOrNull { it.id == current?.id }
                                ?: partial.episodes.firstOrNull { it.id == requestedEpisode }
                                ?: library.resumeEpisode(partial)
                            detailLoading = false
                        }
                    }
                })
                if (page == Page.DETAIL) {
                    val current = episode
                    selected = loaded
                    episode = loaded.episodes.firstOrNull { it.id == current?.id }
                        ?: loaded.episodes.firstOrNull { it.id == requestedEpisode }
                        ?: library.resumeEpisode(loaded)
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { detailError = "Impossible de charger cette fiche. ${e.message.orEmpty()}"
            } finally { detailLoading = false }
        }
    }

    fun resolve(play: Boolean, refresh: Boolean = false, target: Episode? = episode, chosenLanguage: String = language) {
        val anime = selected ?: return
        prefetchJob?.cancel()
        failedVideos.clear(); retryUsed = false
        resolveJob?.cancel(); streamLoading = true; sources = emptyList(); detailError = ""; playerError = ""
        resolveJob = scope.launch {
            try {
                var started = false
                val resolved = withTimeoutOrNull(60_000) { manager.resolveSources(anime, target, chosenLanguage, refresh, onPartial = { partial ->
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        sources = partial
                        if (play && !started) {
                            started = true; episode = target; source = partial.first(); currentQuality = "Détection…"; page = Page.PLAYER
                        }
                    }
                }) }
                if (resolved != null) sources = resolved
                if (sources.isEmpty()) detailError = "Aucun serveur disponible en ${if (language == "Toutes") "VF ou VOSTFR" else language}. Actualisez les sources ou choisissez un autre épisode."
                else if (play && !started) { episode = target; source = sources.first(); currentQuality = "Détection…"; page = Page.PLAYER }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { detailError = "La source ne répond pas. ${e.message.orEmpty()}"
            } finally { streamLoading = false }
        }
    }

    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { query = it; page = Page.SEARCH }
    }
    val pairedHost by RemoteLink.target.collectAsState()
    var receivedPlayback by remember { mutableStateOf(false) }
    var receivedQuery by remember { mutableStateOf<String?>(null) }
    var remoteError by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        // Health checks are explicitly requested from Sources, not on every startup.
        runCatching { RemoteLink.start(context) }
        for (command in RemoteLink.commands) {
            when (command.action) {
                "search" -> {
                    if (page != Page.PLAYER) navigate(Page.SEARCH)
                    receivedQuery = command.query
                    query = command.query; category = command.category
                }
                "toggle", "seek", "sleep" -> if (page == Page.PLAYER) { RemoteLink.playerCommands.trySend(command); Unit } else Unit
                "previous", "next", "episode" -> {
                    val entries = selected?.episodes.orEmpty().distinctBy { it.seasonNumber to it.number }
                    val index = entries.indexOfFirst { it.id == episode?.id }
                    val target = if (command.action == "episode") entries.firstOrNull { it.id == command.episode?.id }
                        else entries.getOrNull(index + if (command.action == "next") 1 else -1)
                    target?.let { resolve(true, target = it) }
                }
                "preload" -> {
                    val media = command.anime
                    if (media != null && page == Page.PLAYER && media.id == selected?.id) {
                        manager.rememberSources(media, command.episode, command.language, command.sources)
                        scope.launch {
                            try { command.sources.firstOrNull()?.let { PlaybackCache.prefetch(context, it) } }
                            catch (e: CancellationException) { throw e }
                            catch (_: Exception) { }
                        }
                    }
                }
                "play" -> {
                    detailJob?.cancel(); resolveJob?.cancel()
                    receivedPlayback = true
                    selected = command.anime; episode = command.episode
                    language = command.language
                    command.anime?.let { manager.rememberSources(it, command.episode, command.language, command.sources) }
                    sources = command.sources; source = sources.firstOrNull()
                    playerError = ""; page = Page.PLAYER
                }
            }
        }
    }
    LaunchedEffect(page) {
        if (page == Page.SOURCES && providerNames.isEmpty()) providerNames = manager.getProviderNames()
    }
    DisposableEffect(library) {
        val prefs = context.getSharedPreferences("tvsama_library", 0)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "history" || key == "hidden_resume" || key == "hidden_resume_times") history = library.continuing()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    LaunchedEffect(pairedHost) {
        if (pairedHost != null) while (true) {
            try { RemoteLink.sync(context); history = library.continuing() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { }
            delay(3000)
        }
    }
    // Resolve adjacent episodes while playback is running; cancel when selection changes.
    LaunchedEffect(page, selected?.id, episode?.id, language) {
        if (page == Page.PLAYER && selected?.tag == "Série" && !receivedPlayback) {
            prefetchJob = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]
            delay(5000)
            val anime = selected ?: return@LaunchedEffect
            val entries = anime.episodes.distinctBy { it.seasonNumber to it.number }
            val index = entries.indexOfFirst { it.id == episode?.id }
            for (next in listOfNotNull(entries.getOrNull(index + 1))) {
                try { kotlinx.coroutines.withTimeoutOrNull(12_000) { manager.resolveSources(anime, next, language).firstOrNull()?.let { PlaybackCache.prefetch(context, it) } } }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { }
            }
        }
    }
    LaunchedEffect(page, detailLoading, selected?.id, episode?.id, language) {
        if (page == Page.DETAIL && !detailLoading && selected != null && sources.isEmpty() && !streamLoading) resolve(false)
    }
    LaunchedEffect(query, category, pairedHost) {
        if (pairedHost != null && query.isNotBlank() && query != receivedQuery) {
            delay(700)
            try { RemoteLink.send(context, RemoteCommand(action = "search", query = query, category = category)) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { remoteError = "Recherche non envoyée : vérifiez que la télévision est ouverte sur le même Wi-Fi." }
        }
    }
    LaunchedEffect(page, source, pairedHost) {
        if (page != Page.PLAYER) receivedPlayback = false
        if (page == Page.PLAYER && source != null && pairedHost != null && !receivedPlayback) {
            try {
                RemoteLink.send(context, RemoteCommand(action = "play", anime = selected, episode = episode,
                    sources = listOf(source!!) + sources.filter { it.url != source!!.url }, language = language))
                page = if (selected?.tag == "Direct") Page.LIVE else Page.DETAIL
                Toast.makeText(context, "Lecture lancée sur la télévision", Toast.LENGTH_SHORT).show()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                page = if (selected?.tag == "Direct") Page.LIVE else Page.DETAIL
                remoteError = "Lecture non envoyée : vérifiez la connexion de la télévision, puis réessayez."
            }
        }
    }
    if (remoteError.isNotBlank()) AlertDialog(onDismissRequest = { remoteError = "" },
        title = { Text("Télécommande") }, text = { Text(remoteError) },
        confirmButton = { TextButton(onClick = { remoteError = "" }) { Text("Fermer") } },
        dismissButton = { TextButton(onClick = { RemoteLink.disconnect(context); remoteError = "" }) { Text("Délier") } })

    LaunchedEffect(query, category, generation, page == Page.HOME || page == Page.SEARCH) {
        if (page != Page.HOME && page != Page.SEARCH) return@LaunchedEffect
        if (context.isTelevision() && query.isBlank() && generation == 0 && category == "Tous") return@LaunchedEffect
        pageJob?.cancel(); loadingMore = false
        catalogLoading = true; catalogError = ""; catalogPage = 1
        try {
            if (query.isNotBlank()) { delay(250); library.rememberSearch(query); recentSearches = library.searches() }
            val key = "$category|$query"
            if (catalogKey != key) { catalog = emptyList(); catalogKey = key }
            val discoveries = query.isBlank() && category == "Tous"
            val result = manager.searchAllProviders(query, category, onPartial = { partial ->
                // Search results are rendered as soon as each provider answers;
                // the final merge below keeps the same ordering and identity rules.
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (discoveries) appendCatalogue(partial) else catalog = partial
                    catalogLoading = false
                    catalogError = ""
                }
            })
            if (discoveries) appendCatalogue(result) else catalog = result
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
            Page.PLAYER -> { resolveJob?.cancel(); page = if (selected?.tag == "Direct") Page.LIVE else Page.DETAIL; history = library.continuing() }
            Page.DETAIL -> { detailJob?.cancel(); resolveJob?.cancel(); page = returnPage }
            else -> navigate(Page.HOME)
        }
    }

    if (unavailableSources.isNotEmpty() && !inPip) AlertDialog(onDismissRequest = { unavailableSources = emptyList() },
        title = { Text("Sources indisponibles") },
        text = { Text(unavailableSources.joinToString("\n")) },
        confirmButton = { TextButton(onClick = { unavailableSources = emptyList(); navigate(Page.SOURCES) }) { Text("Voir les sources") } },
        dismissButton = { TextButton(onClick = { unavailableSources = emptyList() }) { Text("Fermer") } })
    Surface(Modifier.fillMaxSize(), color = Ink) {
        if (page == Page.PLAYER && source != null && (pairedHost == null || receivedPlayback)) {
            Column(Modifier.fillMaxSize().then(if (inPip) Modifier else Modifier.safeDrawingPadding())) {
                if (!inPip && !isFullscreen && playerControlsVisible && sources.size > 1) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                    items(sources) { candidate ->
                        val selectedQuality = if (candidate.url == source?.url && currentQuality != "Détection…") currentQuality else candidate.quality
                        Action("${candidate.provider} · $selectedQuality · ${candidate.latencyMs.takeIf { it > 0 } ?: "?"} ms", selected = candidate.url == source?.url) {
                            source = candidate; currentQuality = "Détection…"; playerError = ""
                        }
                    }
                }
                if (!inPip && !isFullscreen && playerError.isNotBlank()) {
                    Text(playerError, color = Color(0xFFFFB4AB), modifier = Modifier.padding(12.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        sources.filter { it.url != source?.url }.forEach { alternative -> Action("${alternative.provider} · ${alternative.name}") { source = alternative; currentQuality = "Détection…"; playerError = "" } }
                        Action("Actualiser") { if (selected?.tag == "Direct") page = Page.LIVE else { page = Page.DETAIL; resolve(false, true) } }
                        Action("Fermer") { playerError = "" }
                    }
                }
                val playingAnime = selected
                val playingEpisode = episode
                val entries = playingAnime?.episodes.orEmpty().distinctBy { it.seasonNumber to it.number }
                val episodeIndex = entries.indexOfFirst { it.number == playingEpisode?.number && it.seasonNumber == playingEpisode?.seasonNumber }
                key(livePlaybackRevision) { TvSamaPlayer(source = source!!, anime = playingAnime, episode = playingEpisode, title = "${playingAnime?.title.orEmpty()}${playingEpisode?.title?.let { " · $it" }.orEmpty()}", poster = playingAnime?.poster.orEmpty(),
                    onBack = { resolveJob?.cancel(); page = if (playingAnime?.tag == "Direct") Page.LIVE else Page.DETAIL; history = library.continuing() },
                    imdbId = playingAnime?.imdbId,
                    isMovie = playingAnime?.tag == "Film",
                    seasonNumber = if (playingAnime?.tag == "Film") 0 else playingEpisode?.introSeason ?: playingEpisode?.seasonNumber ?: 0,
                    episodeNumber = if (playingAnime?.tag == "Film") 0 else playingEpisode?.introNumber ?: playingEpisode?.number ?: 0,
                    onPrevious = if (playingAnime?.tag != "Film" && episodeIndex > 0) ({ resolve(true, target = entries[episodeIndex - 1]) }) else null,
                    onNext = if (playingAnime?.tag != "Film" && episodeIndex >= 0 && episodeIndex + 1 < entries.size) ({ resolve(true, target = entries[episodeIndex + 1]) }) else null,
                    resumeAt = playingAnime?.let { library.progress(it, playingEpisode) } ?: 0,
                    subtitlesEnabled = subtitlesEnabled,
                    onActualQuality = { currentQuality = it },
                    isFullscreen = isFullscreen,
                    onControlsVisibleChange = { playerControlsVisible = it },
                    onToggleFullscreen = {
                        isFullscreen = !isFullscreen
                        setPlayerFullscreen(hostActivity, isFullscreen)
                    },
                    onProgress = { position, duration -> playingAnime?.takeUnless { it.tag == "Direct" }?.let { library.save(it, playingEpisode, position, duration) } },
                    modifier = Modifier.weight(1f).fillMaxWidth(), onError = { message ->
                        playerError = message
                        val failed = source?.url
                        if (playingAnime?.tag == "Direct") {
                            val serverId = source?.serverId?.takeIf { it.isNotBlank() }
                            val refreshCurrentServer = serverId != null && refreshedLiveServers.add(serverId)
                            if (!refreshCurrentServer) serverId?.let { failedLiveServers.add(it) }
                            sources = sources.filter { it.url != failed }
                            val alternative = sources.firstOrNull()
                            if (alternative != null) { source = alternative; playerError = "" } else {
                                resolveJob?.cancel()
                                resolveJob = scope.launch {
                                    try {
                                        val next = resolveLiveEvent(context, playingAnime.id, failedLiveServers.toSet())
                                        sources = listOf(next); source = next; livePlaybackRevision++; playerError = ""
                                    } catch (e: CancellationException) { throw e }
                                    catch (_: Exception) { playerError = "Les lecteurs de ce direct sont indisponibles. Actualisez pour réessayer." }
                                }
                            }
                        }
                        playingAnime?.takeUnless { it.tag == "Direct" }?.let { animeForRetry ->
                            failed?.let { failedVideos.add(it) }
                            val alternative = sources.firstOrNull { it.url !in failedVideos }
                            if (alternative != null) {
                                source = alternative; currentQuality = "Détection…"; playerError = ""
                            } else if (!retryUsed) {
                                retryUsed = true; prefetchJob?.cancel(); resolveJob?.cancel()
                                resolveJob = scope.launch {
                                    try {
                                        val refreshed = manager.resolveSources(animeForRetry, playingEpisode, language, refresh = true)
                                        sources = refreshed.filter { it.url !in failedVideos }
                                        if (sources.isNotEmpty()) { source = sources.first(); currentQuality = "Détection…"; playerError = "" }
                                        else playerError = "Aucun autre serveur lisible pour cet épisode. Choisissez une autre version ou réessayez plus tard."
                                    } catch (e: CancellationException) { throw e }
                                    catch (_: Exception) { playerError = "Les serveurs ne répondent pas. Réessayez plus tard." }
                                }
                            }
                        }
                    },
                    onEnded = {
                        val entries = selected?.episodes.orEmpty().distinctBy { it.seasonNumber to it.number }
                        val index = entries.indexOfFirst { it.number == episode?.number && it.seasonNumber == episode?.seasonNumber }
                        if (playingAnime?.tag != "Direct" && autoplay && index >= 0 && index + 1 < entries.size) {
                            resolve(true, target = entries[index + 1])
                        }
                    }) }
            }
        } else BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 840.dp
            Row(Modifier.fillMaxSize()) {
                if (wide && menuExpanded) Column(Modifier.onFocusChanged { if (it.hasFocus) menuHadFocus = true else if (menuHadFocus) { menuExpanded = false; menuHadFocus = false } }.focusGroup().width(190.dp).fillMaxHeight().padding(start = 24.dp, end = 18.dp, top = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        Action("☰", Modifier.onFocusChanged { if (it.isFocused && wide) menuExpanded = true }) { menuExpanded = !menuExpanded }
                        if (!wide) Brand() else Text("COLLECTION FRANÇAISE", color = Muted, fontSize = 11.sp, letterSpacing = 2.sp)
                        Spacer(Modifier.weight(1f))
                        Text("Leon Made <3", color = Color(0xFFFF79B9), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Spacer(Modifier.width(12.dp)); Action("Recherche", Modifier.heightIn(min = 44.dp)) { navigate(Page.SEARCH) }
                        Spacer(Modifier.width(8.dp)); CastRouteButton(Modifier.size(44.dp))
                    }
                    if (!wide && menuExpanded) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                        items(Page.entries.filter { it != Page.DETAIL && it != Page.PLAYER && it != Page.SEARCH }) { destination ->
                            Action(destination.label, selected = page == destination) { navigate(destination) }
                        }
                    }
                    if (pairedHost != null) RemoteControls()
                    when (page) {
                        Page.HOME, Page.SEARCH -> {
                            val keyboard = LocalSoftwareKeyboardController.current
                            val focusManager = LocalFocusManager.current
                            if (page == Page.SEARCH) {
                                val searchFocus = remember { FocusRequester() }
                                LaunchedEffect(Unit) { searchFocus.requestFocus(); keyboard?.show() }
                                SectionTitle("Qu’allez-vous regarder ?", "Une recherche dans vos catalogues français.")
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(query, { query = it }, Modifier.weight(1f).focusRequester(searchFocus), placeholder = { Text("Film, série, animation…") }, singleLine = true)
                                    Action("Micro") {
                                        try { voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")) }
                                        catch (_: Exception) { catalogError = "La recherche vocale n’est pas disponible sur cet appareil." }
                                    }
                                }
                            }
                            if (page == Page.SEARCH && recentSearches.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    recentSearches.chunked(6).take(2).forEach { row ->
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(row) { text ->
                                            TextButton(onClick = { query = text; keyboard?.hide(); focusManager.clearFocus() }) { Text(text, maxLines = 1) }
                                        } }
                                    }
                                }
                            }
                            if (context.isTelevision() && catalog.isEmpty() && query.isBlank()) {
                                Text("Scannez le QR avec votre téléphone pour rechercher et lancer un média.", color = Muted)
                                Action("Parcourir le catalogue sur la TV") { generation++ }
                            }
                            CategoryRail(category) { if (it == "Diffusions en direct") navigate(Page.LIVE) else category = it }
                            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.End) { Action("↻ Actualiser") { refreshSources() } }
                            if (catalogLoading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color.White)
                            if (catalogError.isNotBlank()) Text(catalogError, color = Muted, modifier = Modifier.padding(vertical = 14.dp))
                            LazyVerticalGrid(columns = GridCells.Adaptive(if (wide) 145.dp else 125.dp), state = if (page == Page.HOME) homeGridState else searchGridState,
                                modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
                                if (page == Page.HOME && catalog.isNotEmpty()) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                    FeaturedCarousel(catalog.take(8), wide) { openDetails(it) }
                                }
                                if (page == Page.HOME && history.isNotEmpty()) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text("Reprendre", style = MaterialTheme.typography.titleLarge)
                                        LazyRow(state = resumeRowState, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            items(history, key = { cardKey(it.anime) }) { entry -> ResumeCard(entry, onRemove = { library.hideResume(entry.anime); history = library.continuing() }) { openDetails(entry.anime, entry.episodeId) } }
                                        }
                                    }
                                }
                                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                    Text(if (page == Page.SEARCH) "${catalog.size} titres" else "À découvrir", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
                                }
                                if (page == Page.HOME) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                    LazyRow(state = discoveryRowState, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                        items(catalog.drop(1), key = ::cardKey) { anime -> PosterCard(anime, cardModifier(anime).width(155.dp)) { openDetails(anime) } }
                                    }
                                } else items(catalog, key = ::cardKey) { anime -> PosterCard(anime, cardModifier(anime)) {
                                    keyboard?.hide(); focusManager.clearFocus(); openDetails(anime)
                                } }
                                if (catalog.isNotEmpty()) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                    Action(if (loadingMore) "Chargement…" else "Afficher davantage", enabled = !loadingMore) {
                                        pageJob = scope.launch {
                                            loadingMore = true
                                            try {
                                                val next = manager.searchAllProviders(query, category, catalogPage + 1)
                                                if (next.isEmpty()) catalogError = "Vous avez atteint la fin des résultats disponibles."
                                                else { appendCatalogue(next); catalogPage++ }
                                            } catch (e: CancellationException) { throw e } catch (_: Exception) { catalogError = "Impossible de charger la suite." } finally { loadingMore = false }
                                        }
                                    }
                                }
                            }
                        }
                        Page.DETAIL -> selected?.let { anime ->
                            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
                                item { Action("‹ ${returnPage.label}") { page = returnPage; detailJob?.cancel(); resolveJob?.cancel() } }
                                if (!wide) item {
                                    Artwork(anime.poster.ifBlank { anime.banner }, anime.title,
                                        Modifier.fillMaxWidth().height(240.dp), crop = false)
                                }
                                item {
                                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                        if (wide) Artwork(anime.poster, anime.title, Modifier.width(175.dp).height(260.dp))
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Text(anime.tag.uppercase(), color = Accent, fontSize = 12.sp, letterSpacing = 2.sp)
                                            SectionTitle(anime.title)
                                            Text((listOfNotNull(anime.year?.toString()) + anime.genres).joinToString(" · "), color = Muted)
                                            if (anime.tag == "Série") Text(listOfNotNull(
                                                anime.firstAired?.let { "Première diffusion : $it" },
                                                anime.seriesStatus ?: "Statut non communiqué",
                                                anime.lastAired?.takeIf { anime.seriesStatus in listOf("Terminée", "Annulée") }?.let { "Dernière diffusion : $it" }
                                            ).joinToString(" · "), color = Muted)
                                            anime.metadataUrl?.let { url -> TextButton(onClick = {
                                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                            }) { Text("Métadonnées TVmaze · CC BY-SA", color = Muted) } }
                                            Text(anime.description.ifBlank { "Le synopsis n’est pas disponible pour ce titre." }, color = Color(0xFFE0E0E0))
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                listOf("VF", "VOSTFR").forEach { version ->
                                                    Action(version, selected = language == version, enabled = !streamLoading) {
                                                        language = version; library.setLanguage(version)
                                                        sources = emptyList(); detailError = ""
                                                    }
                                                }
                                            }
                                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Action(if (streamLoading && sources.isEmpty()) "Recherche des serveurs…" else if (library.progress(anime, episode) > 0) "▶ Reprendre" else "▶ Regarder", primary = true, enabled = !detailLoading && (!streamLoading || sources.isNotEmpty())) {
                                                    if (sources.isNotEmpty()) { source = sources.first(); page = Page.PLAYER }
                                                    else resolve(true, target = episode ?: library.resumeEpisode(anime))
                                                }
                                                val inMyList = favorites.any { LibraryStore.sameFavorite(it, anime) }
                                                Action(if (inMyList) "✓ Retirer de ma liste" else "+ Ma liste") {
                                                    library.setFavorite(anime, !inMyList)
                                                    favorites = library.favorites()
                                                }
                                            }
                                        }
                                    }
                                }
                                if (detailLoading || streamLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color.White) }
                                if (detailError.isNotBlank()) item { Text(detailError, color = Color(0xFFFFB4AB)); Action("Réessayer") { if (detailLoading || anime.episodes.isEmpty() && anime.tag != "Film") openDetails(anime) else resolve(false, true) } }
                                if (anime.tag != "Film" && anime.episodes.isNotEmpty()) item {
                                    EpisodePicker(anime, library, episode, onSelect = { chosen -> episode = chosen; sources = emptyList(); detailError = ""; resolve(true, target = chosen) })
                                }
                                item {
                                    Text("Versions et serveurs", style = MaterialTheme.typography.titleLarge)
                                    Text("Les serveurs disponibles sont classés par qualité. Vous gardez le choix.", color = Muted, modifier = Modifier.padding(vertical = 8.dp))
                                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            LazyVerticalGrid(GridCells.Adaptive(145.dp), Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) { items(favorites) { anime ->
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    PosterCard(anime) { openDetails(anime) }
                                    Action("Retirer", Modifier.fillMaxWidth()) {
                                        library.setFavorite(anime, false)
                                        favorites = library.favorites()
                                    }
                                }
                            } }
                        }
                        Page.HISTORY -> {
                            SectionTitle("Reprendre", "Retrouvez votre épisode et votre position de lecture.")
                            if (history.isEmpty()) EmptyState("Rien en cours pour le moment", "Vos lectures apparaîtront ici automatiquement.")
                            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                items(history) { entry ->
                                    ResumeCard(entry, onRemove = { library.hideResume(entry.anime); history = library.continuing() }) { openDetails(entry.anime, entry.episodeId) }
                                }
                            }
                        }
                        Page.LIVE -> LiveScreen { event, videos ->
                            selected = Anime(event.title, "Direct", emptyList(), id = event.url, provider = "Volkamax Direct")
                            episode = null; sources = videos; source = videos.first(); returnPage = Page.LIVE; page = Page.PLAYER
                        }
                        Page.SOURCES -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            SectionTitle("Vos sources", "Catalogues intégrés et accès aux sites encore non intégrés.")
                            Action("↻ Actualiser les catalogues") { refreshSources() }
                            providerNames.forEach { name ->
                                val external = manager.externalSource(name)
                                val enabled = remember(name, providerRevision) { manager.isProviderEnabled(name) }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Column(Modifier.weight(1f)) {
                                        Text(name, fontWeight = FontWeight.SemiBold)
                                        val state = manager.sourceHealth()[name]
                                        Text(if (external != null) "Lien externe · non intégré" else state ?: "○ Intégré · non vérifié",
                                            color = when {
                                                state?.startsWith("✕") == true -> Coral
                                                state?.startsWith("⚠") == true -> Color(0xFFFFC857)
                                                state?.startsWith("✓") == true -> Accent
                                                else -> Muted
                                            },
                                            fontWeight = FontWeight.SemiBold)
                                        Text(external?.url ?: (state ?: "Vérification au prochain actualiser"), color = Muted, fontSize = 12.sp, maxLines = 2)
                                        external?.let {
                                            Text(it.limitation, color = Muted, fontSize = 12.sp, maxLines = 2)
                                        }
                                    }
                                    if (external == null) Action(if (enabled) "Activé" else "Désactivé", selected = enabled) { manager.setProviderEnabled(name, !enabled); unavailableSources = unavailableSources.filter(manager::isProviderEnabled); providerRevision++; generation++ }
                                    manager.sourceUrl(name)?.let { url -> Action("Accès") { sourceAccess = name to url } }
                                }
                                HorizontalDivider(color = Line)
                            }
                            SourceDirectoryUi()
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
private fun FeaturedCarousel(titles: List<Anime>, wide: Boolean, onOpen: (Anime) -> Unit) {
    if (titles.isEmpty()) return
    val pager = rememberPagerState(pageCount = { titles.size })
    val scope = rememberCoroutineScope()
    fun move(direction: Int) {
        if (!pager.isScrollInProgress && titles.size > 1) scope.launch {
            val next = (pager.currentPage + direction + titles.size) % titles.size
            pager.animateScrollToPage(next, animationSpec = tween(260))
        }
    }
    Column {
        HorizontalPager(state = pager, key = { "${titles[it].provider}|${titles[it].id}" },
            modifier = Modifier.fillMaxWidth()) { index ->
            Box(Modifier.graphicsLayer {
                val distance = kotlin.math.abs((pager.currentPage - index) + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
                alpha = 1f - distance * 0.12f
            }) {
                Hero(titles[index], wide, active = index == pager.currentPage && !pager.isScrollInProgress) { onOpen(titles[index]) }
            }
        }
        if (titles.size > 1) Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                titles.indices.forEach { index ->
                    Box(Modifier.size(width = if (index == pager.currentPage) 18.dp else 6.dp, height = 3.dp)
                        .background(if (index == pager.currentPage) Accent else Line))
                }
            }
            Text("${pager.currentPage + 1} / ${titles.size}", color = Muted, fontSize = 12.sp)
            Action("‹", Modifier.semantics { contentDescription = "Affiche précédente" }, enabled = !pager.isScrollInProgress) { move(-1) }
            Action("›", Modifier.semantics { contentDescription = "Affiche suivante" }, enabled = !pager.isScrollInProgress) { move(1) }
        }
    }
}

@Composable
private fun Hero(anime: Anime, wide: Boolean, active: Boolean = true, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(if (wide) 310.dp else 280.dp)) {
        Artwork(anime.banner.ifBlank { anime.poster }, anime.title, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Ink, Ink.copy(alpha = .7f), Color.Transparent))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Ink))))
        Column(Modifier.align(Alignment.BottomStart).widthIn(max = 500.dp).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("À L’AFFICHE", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Text(anime.title, color = Color.White, fontSize = if (wide) 36.sp else 28.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            Text(listOfNotNull(anime.tag, anime.year?.toString()).joinToString(" · "), color = Muted)
            Action("Découvrir  →", primary = true, enabled = active, onClick = onClick)
        }
    }
}

@Composable
private fun EpisodePicker(anime: Anime, library: LibraryStore, selected: Episode?, onSelect: (Episode) -> Unit) {
    val episodes = anime.episodes
    val fallbackPoster = anime.poster
    val visibleEpisodes = episodes.distinctBy { "${it.seasonNumber}|${it.number}" }
    val seasons = visibleEpisodes.map { it.seasonNumber }.distinct().sortedWith(compareBy<Int> { it == 0 }.thenBy { it })
    var season by remember(episodes, selected?.seasonNumber) { mutableStateOf(selected?.seasonNumber?.takeIf { it in seasons } ?: seasons.firstOrNull()) }
    val listState = rememberLazyListState()
    val seasonEntries = visibleEpisodes.filter { it.seasonNumber == season }.sortedBy { it.number }
    LaunchedEffect(season, selected?.id) {
        val index = seasonEntries.indexOfFirst { it.number == selected?.number }
        if (index >= 0) listState.scrollToItem(index)
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Épisodes", style = MaterialTheme.typography.titleLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(seasons) { number -> Action(if (number == 0) "Épisodes spéciaux" else "Saison $number", selected = season == number) { season = number } } }
        LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(seasonEntries) { entry ->
                Column(Modifier.width(225.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Artwork(entry.poster.ifBlank { fallbackPoster }, entry.title, Modifier.fillMaxWidth().height(120.dp))
                    Text(if (entry.seasonNumber == 0) "Spécial ${entry.number}" else "S${entry.seasonNumber} · Épisode ${entry.number}", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(entry.title, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
                    if (entry.description.isNotBlank()) Text(entry.description, color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val progress = library.fraction(anime, entry)
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = Accent)
                    if (progress >= 1f) Text("✓ Lu", color = Accent)
                    Action("▶ Regarder", Modifier.fillMaxWidth(), selected = selected?.id == entry.id) { onSelect(entry) }
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
