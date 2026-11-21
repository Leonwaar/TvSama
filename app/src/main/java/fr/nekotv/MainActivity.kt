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
import androidx.compose.foundation.border
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        setIntent(Intent(intent).setData(null))
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

internal fun setPlayerFullscreen(activity: Activity?, fullscreen: Boolean) {
    if (activity == null) return
    activity.window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK))
    @Suppress("DEPRECATION")
    activity.window.statusBarColor = android.graphics.Color.BLACK
    @Suppress("DEPRECATION")
    activity.window.navigationBarColor = android.graphics.Color.BLACK
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        activity.window.isNavigationBarContrastEnforced = !fullscreen
        activity.window.isStatusBarContrastEnforced = !fullscreen
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) activity.window.attributes = activity.window.attributes.apply {
        layoutInDisplayCutoutMode = if (fullscreen) android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            else android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        activity.window.insetsController?.let { controller ->
            controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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

internal enum class Page(val label: String) { HOME("Accueil"), SEARCH("Recherche"), FAVORITES("Ma liste"), HISTORY("Reprendre"), SOURCES("Sources"), TV("TV"), LIVE("Direct"), SETTINGS("Réglages"), REMOTE("Télécommande"), DETAIL("Fiche"), PLAYER("Lecture") }
internal fun startupPage(value: String?) = when (value) {
    "Reprendre" -> Page.HISTORY
    "Ma liste" -> Page.FAVORITES
    else -> Page.HOME
}

@Composable
private fun TvSamaApp() {
    val context = LocalContext.current
    val compactPosters = CompactPosters.current
    UpdatePrompt()
    val manager = remember { StreamFlixProviderManager.getInstance() }
    val cinema = remember { CinemaSpotlight(context) }
    var featured by remember { mutableStateOf(cinema.cached(manager)) }
    val library = remember { LibraryStore(context) }
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf(if ((context as? Activity)?.intent?.getBooleanExtra("open_live", false) == true) Page.LIVE
        else startupPage(context.getSharedPreferences("tvsama_settings", 0).getString("startup", null))) }
    var returnPage by remember { mutableStateOf(Page.HOME) }
    var showWatched by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("Tous") }
    var language by rememberSaveable { mutableStateOf(library.language()) }
    var catalog by remember { mutableStateOf<List<Anime>>(emptyList()) }
    var catalogKey by remember { mutableStateOf("Tous|") }
    var loadedCatalogKey by remember { mutableStateOf("") }
    var loadedGeneration by remember { mutableIntStateOf(-1) }
    val homeGridState = rememberLazyGridState()
    val searchGridState = rememberLazyGridState()
    val resumeRowState = rememberLazyListState()
    val catalogueFocus = remember { FocusRequester() }
    var lastHomeFocus by remember { mutableStateOf<String?>(null) }
    var lastSearchFocus by remember { mutableStateOf<String?>(null) }
    fun cardKey(anime: Anime) = "${anime.provider}|${anime.tag}|${anime.id}"
    // A card whose every source is disabled offers no playable stream: it is not displayed.
    fun hasEnabledSource(anime: Anime) = anime.provider.isBlank() || manager.isProviderEnabled(anime.provider) ||
        anime.references.any { manager.isProviderEnabled(it.provider) }
    fun playableCards(cards: List<Anime>) = cards.filter(::hasEnabledSource)
    fun playableHistory(cards: List<SavedPlayback>) = cards.filter { hasEnabledSource(it.anime) }
    fun cardModifier(anime: Anime): Modifier {
        val key = cardKey(anime)
        val previous = if (page == Page.HOME) lastHomeFocus else lastSearchFocus
        return (if (key == previous) Modifier.focusRequester(catalogueFocus) else Modifier)
            .onFocusChanged { if (it.isFocused) {
                if (page == Page.HOME) lastHomeFocus = key else if (page == Page.SEARCH) lastSearchFocus = key
            } }
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
    var cinemaError by remember { mutableStateOf("") }
    var cinemaRevision by remember { mutableIntStateOf(0) }
    var lastCinemaRequest by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(generation, cinemaRevision, page == Page.HOME) {
        if (page != Page.HOME) return@LaunchedEffect
        featured = cinema.cached(manager)
        val request = generation to cinemaRevision
        if (featured.isEmpty() || cinema.needsRefresh() || lastCinemaRequest != request) {
            cinemaError = ""
            try { cinema.refresh(manager) { featured = it }; lastCinemaRequest = request }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { cinemaError = e.message ?: "Sorties cinéma indisponibles" }
        }
    }
    var catalogPage by remember { mutableIntStateOf(1) }
    var loadingMore by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }
    var pagingError by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Anime?>(null) }
    var episode by remember { mutableStateOf<Episode?>(null) }
    var source by remember { mutableStateOf<VideoSource?>(null) }
    var preferredSource by remember { mutableStateOf<VideoSource?>(null) }
    var sources by remember { mutableStateOf<List<VideoSource>>(emptyList()) }
    var detailLoading by remember { mutableStateOf(false) }
    var streamLoading by remember { mutableStateOf(false) }
    var launching by remember { mutableStateOf(false) }
    var detailError by remember { mutableStateOf("") }
    var playerError by remember { mutableStateOf("") }
    val failedLiveServers = remember(selected?.id) { mutableSetOf<String>() }
    var livePlaybackRevision by remember(selected?.id) { mutableIntStateOf(0) }
    val failedVideos = remember(selected?.id, episode?.id, language) { mutableSetOf<String>() }
    var retryUsed by remember(selected?.id, episode?.id, language) { mutableStateOf(false) }
    var prefetchJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var serverPrefetchJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var favorites by remember { mutableStateOf(playableCards(library.favorites())) }
    var history by remember { mutableStateOf(playableHistory(library.continuing())) }
    var historyBackup by remember { mutableStateOf(library.hasHistoryBackup()) }
    var recommendationHistory by remember { mutableStateOf(library.history()) }
    var autoplay by remember { mutableStateOf(library.autoplay()) }
    var subtitlesEnabled by remember { mutableStateOf(context.getSharedPreferences("tvsama_settings", 0).getBoolean("subtitles", true)) }
    DisposableEffect(context) {
        val preferences = context.getSharedPreferences("tvsama_settings", 0)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == "subtitles") subtitlesEnabled = preferences.getBoolean("subtitles", true)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    var currentQuality by remember { mutableStateOf("Détection…") }
    var isFullscreen by remember { mutableStateOf(false) }
    var playerControlsVisible by remember { mutableStateOf(true) }
    val hostActivity = context as? Activity
    val inPip = (hostActivity as? MainActivity)?.inPictureInPicture == true
    var providerNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var providerRevision by remember { mutableIntStateOf(0) }
    var unavailableSources by remember { mutableStateOf(
        if (context.getSharedPreferences("tvsama_settings", 0).getBoolean("show_unavailable_startup", true))
            context.getSharedPreferences("tvsama_settings", 0).getStringSet("unavailable_sources", emptySet()).orEmpty().filter(manager::isProviderEnabled)
        else emptyList()) }
    var resolveJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var resolveRevision by remember { mutableIntStateOf(0) }
    var pageJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var searchMatches by remember { mutableStateOf(playableCards(library.searchMatches())) }
    var recentSearches by remember { mutableStateOf(library.searches()) }
    var discoveryCards by remember { mutableStateOf<List<Anime>>(emptyList()) }
    val discoveryCatalogue by rememberUpdatedState(catalog.takeIf { catalogKey == "$category|$query" }.orEmpty() to (catalogLoading || loadingMore))
    LaunchedEffect(page == Page.HOME, category, query, recommendationHistory, favorites, recentSearches, searchMatches) {
        if (page != Page.HOME || query.isNotBlank()) return@LaunchedEffect
        if (discoveryCards.isEmpty()) {
            snapshotFlow { discoveryCatalogue }.first { it.first.isNotEmpty() }
            delay(750)
        }
        snapshotFlow { discoveryCatalogue }.collectLatest { (incoming, loading) ->
            if (incoming.isEmpty() && loading) return@collectLatest
            // Only update if we have new content or if we previously had content that should persist
            val previous = discoveryCards
            if (previous.isNotEmpty() && incoming.isEmpty()) {
                // Keep the previous content if we had something before
                return@collectLatest
            }
            discoveryCards = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                try {
                    val ranked = Recommendations.rank(incoming, recommendationHistory, favorites, recentSearches, searchMatches)
                    Recommendations.displayed(incoming, ranked, previous, loading)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Fallback to previous if there's an error
                    previous.ifEmpty { incoming }
                }
            }
        }
    }
    LaunchedEffect(page) {
        val key = if (page == Page.HOME) lastHomeFocus else if (page == Page.SEARCH) lastSearchFocus else null
        val cards = if (page == Page.HOME && query.isBlank()) discoveryCards else catalog
        if (context.isTelevision() && key != null && cards.any { cardKey(it) == key }) {
            withFrameNanos { }
            runCatching { catalogueFocus.requestFocus() }
        }
    }
    var menuExpanded by remember { mutableStateOf(false) }
    var menuHadFocus by remember { mutableStateOf(false) }
    val menuFocus = remember { FocusRequester() }
    val menuButtonFocus = remember { FocusRequester() }
    LaunchedEffect(menuExpanded) {
        if (menuExpanded && context.isTelevision()) {
            withFrameNanos { }
            runCatching { menuFocus.requestFocus() }
        }
    }
    var detailJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var sourceAccess by remember { mutableStateOf<Pair<String, String>?>(null) }
    var sourceAccessRetry by remember { mutableStateOf<(() -> Unit)?>(null) }
    var refreshingSources by remember { mutableStateOf(false) }
    var verifyingSource by remember { mutableStateOf<String?>(null) }
    var verifySourceJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var refreshJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var retryPlayback by remember { mutableStateOf<(() -> Unit)?>(null) }
    if (launching) LoadingDialog("Recherche et chargement de la source…") {
        resolveRevision++; resolveJob?.cancel(); launching = false; streamLoading = false
    }
    fun verifyMedia(name: String?) {
        if (verifyingSource != null || refreshingSources) return
        val names = if (name == null) providerNames.filter { manager.isProviderEnabled(it) && manager.externalSource(it) == null } else listOf(name)
        verifySourceJob = scope.launch {
            try { for (candidate in names) {
                verifyingSource = candidate
                if (manager.verifySourceMedia(candidate)) unavailableSources = unavailableSources - candidate
                providerRevision++
            } }
            finally { verifyingSource = null; providerRevision++ }
        }
    }
    fun loadMore() {
        if (loadingMore || catalogLoading || !hasMore) return
        loadingMore = true; pagingError = false
        pageJob = scope.launch {
            try {
                val next = manager.searchAllProviders(query, category, catalogPage + 1)
                val filtered = if (query.isNotBlank()) next.filter { isCatalogueMatch(it, query) } else next
                val previousSize = catalog.size
                appendCatalogue(filtered)
                catalogPage++
                hasMore = catalog.size > previousSize
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { pagingError = true; catalogError = "Impossible de charger la suite. Réessayez." }
            finally { loadingMore = false }
        }
    }
    fun refreshSources() {
        if (refreshingSources) return
        refreshingSources = true
        refreshJob = scope.launch {
            try {
                manager.refreshSources()
                unavailableSources = manager.sourceHealth().filterValues { it.startsWith("✕") }.keys.toList()
                context.getSharedPreferences("tvsama_settings", 0).edit().putStringSet("unavailable_sources", unavailableSources.toSet()).apply()
                providerRevision++; generation++
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { catalogError = "Actualisation impossible. Réessayez depuis Sources. ${e.message.orEmpty()}" }
            finally { refreshingSources = false }
        }
    }
    sourceAccess?.let { (name, url) -> SourceAccess(name, url, { sourceAccess = null }, {
        scope.launch {
            try {
                manager.retrySourceAccess(name)
                if (name == "Direct") retryPlayback?.invoke()
                else if (sourceAccessRetry != null) { val retry = sourceAccessRetry; sourceAccessRetry = null; retry?.invoke() }
                else if (manager.externalSource(name) != null) refreshSources()
                else verifyMedia(name)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                detailError = e.message ?: "Validation de l’adresse impossible. Réessayez depuis Sources."
                playerError = detailError
                verifyMedia(name)
            } finally { providerRevision++ }
        }
    }, playback = name == "Direct", onPlay = if (sourceAccessRetry != null && selected != null) { verified ->
        resolveRevision++; resolveJob?.cancel(); serverPrefetchJob?.cancel()
        launching = false; streamLoading = false; playerError = ""; detailError = ""
        sources = listOf(verified); source = verified
        selected?.let { manager.rememberSources(it, episode, language, sources) }
        sourceAccessRetry = null; providerRevision++; page = Page.PLAYER
    } else null) }
    LaunchedEffect(Unit) {
        refreshSources()
        while (true) {
            delay(30 * 60 * 1000L)
            val visible = (hostActivity as? androidx.lifecycle.LifecycleOwner)?.lifecycle?.currentState
                ?.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) == true
            if (visible && page != Page.PLAYER && !launching && !streamLoading && !refreshingSources && verifyingSource == null && sourceAccess == null) {
                try { manager.refreshDirectory(force = false); providerRevision++ }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { }
            }
        }
    }

    fun navigate(destination: Page) {
        if (launching) return
        detailJob?.cancel(); resolveJob?.cancel(); pageJob?.cancel()
        serverPrefetchJob?.cancel(); serverPrefetchJob = null
        resolveRevision++
        detailLoading = false; streamLoading = false; loadingMore = false
        if (destination == Page.HOME) { query = ""; category = "Tous" }
        if (destination == Page.FAVORITES) showWatched = false
        page = destination; favorites = playableCards(library.favorites()); history = playableHistory(library.continuing())
    }
    fun leavePlayer() {
        resolveRevision++; resolveJob?.cancel(); launching = false; streamLoading = false
        isFullscreen = false; setPlayerFullscreen(hostActivity, false)
        page = if (selected?.tag == "Direct") returnPage else Page.DETAIL
        history = playableHistory(library.continuing())
    }

    /** Start IntroDB as early as the IMDb id is known, not when playback has already begun. */
    fun warmIntro(media: Anime?, target: Episode?) {
        val anime = media ?: return
        val imdb = anime.imdbId?.takeIf { it.startsWith("tt") } ?: return
        val movie = anime.tag == "Film"
        val season = if (movie) 0 else target?.introSeason ?: target?.seasonNumber ?: 0
        val number = if (movie) 0 else target?.introNumber ?: target?.number ?: 0
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { fetchSegments(imdb, season, number, movie) }
        }
    }

    /** One entry point for every way a source is started: keeps the chosen player,
     *  warms the intro and resolves an embed while the loading screen is shown. */
    fun startPlayback(video: VideoSource, target: Episode? = episode) {
        source = video; preferredSource = video
        currentQuality = "Détection…"; playerError = ""
        warmIntro(selected, target ?: selected?.firstEpisode())
        scope.launch(kotlinx.coroutines.Dispatchers.IO) { runCatching { prewarmPlaybackSource(video) } }
        page = Page.PLAYER
    }

    fun openDetails(anime: Anime, requestedEpisode: String? = null) {
        if (anime.tag == "Flux personnel") {
            selected = anime; episode = null; source = VideoSource(anime.title, anime.id, "VF", "Auto", "Personnel")
            sources = listOf(source!!); preferredSource = null; page = Page.PLAYER; return
        }
        detailJob?.cancel(); resolveJob?.cancel(); serverPrefetchJob?.cancel(); serverPrefetchJob = null
        resolveRevision++
        if (page != Page.DETAIL && page != Page.PLAYER) returnPage = page
        selected = anime; episode = null; sources = emptyList(); source = null; detailError = ""; playerError = ""
        preferredSource = null
        page = Page.DETAIL; detailLoading = true; streamLoading = false
        if (anime.provider == "AlloCiné") { detailLoading = false; return }
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
                            warmIntro(partial, episode)
                            detailLoading = false
                            if (serverPrefetchJob == null) {
                                val selectedEpisode = episode
                                serverPrefetchJob = scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    delay(350); manager.prefetchServers(partial, selectedEpisode)
                                }
                            }
                        }
                    }
                })
                if (page == Page.DETAIL) {
                    val previousReferences = selected?.references.orEmpty().size
                    val current = episode
                    selected = loaded
                    episode = loaded.episodes.firstOrNull { it.id == current?.id }
                        ?: loaded.episodes.firstOrNull { it.id == requestedEpisode }
                        ?: library.resumeEpisode(loaded)
                    warmIntro(loaded, episode)
                    if (loaded.references.size > previousReferences) {
                        serverPrefetchJob?.cancel()
                        val selectedEpisode = episode
                        serverPrefetchJob = scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            delay(350); manager.prefetchServers(loaded, selectedEpisode)
                        }
                    }
                } else if (page == Page.PLAYER && selected?.id == anime.id) {
                    val additional = additionalProviderReferences(selected!!, loaded)
                    selected = loaded
                    val playingEpisode = episode
                    if (additional.isNotEmpty() && playingEpisode != null) {
                        resolveJob?.join()
                        if (page == Page.PLAYER && selected?.id == anime.id && episode == playingEpisode) {
                            val extra = manager.resolveSources(loaded.copy(references = additional), playingEpisode, language, refresh = true,
                                onPartial = { partial -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    if (page == Page.PLAYER && selected?.id == anime.id && episode == playingEpisode)
                                        sources = (sources + partial).distinctBy { it.url }
                                } })
                            if (page == Page.PLAYER && selected?.id == anime.id && episode == playingEpisode) {
                                sources = (sources + extra).distinctBy { it.url }
                                manager.rememberSources(loaded, playingEpisode, language, sources)
                            }
                        }
                    }
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { detailError = "Impossible de charger cette fiche. ${e.message.orEmpty()}"
            } finally { detailLoading = false }
        }
    }

    fun resolve(play: Boolean, refresh: Boolean = false, target: Episode? = episode, chosenLanguage: String = language) {
        val anime = selected ?: return
        // Warm IntroDB while the source is negotiated, so the skip button is ready on the
        // first frames instead of waiting for a network round-trip during playback.
        warmIntro(anime, target)
        retryPlayback = { resolve(true, true, target, chosenLanguage) }
        val revision = ++resolveRevision
        // The same player/server is reused for the next episode (subtitles and quality
        // stay consistent); the ranked list remains the fallback.
        val preferred = preferredSource?.takeIf { it.language == chosenLanguage && it.provider != "Personnel" }
        prefetchJob?.cancel()
        serverPrefetchJob?.cancel(); serverPrefetchJob = null
        failedVideos.clear(); retryUsed = false
        resolveJob?.cancel(); streamLoading = true; launching = play; sources = emptyList(); detailError = ""; playerError = ""
        resolveJob = scope.launch {
            try {
                var started = false
                var playbackAnime = anime
                var playbackTarget = target
                val resolved = withTimeoutOrNull(45_000) {
                    if (anime.provider == "AlloCiné") {
                        playbackAnime = manager.findCinemaMovie(anime)
                        if (revision != resolveRevision) return@withTimeoutOrNull emptyList()
                        selected = playbackAnime; playbackTarget = playbackAnime.firstEpisode()
                        warmIntro(playbackAnime, playbackTarget)
                    }
                    val receiveSources: suspend (List<VideoSource>) -> Unit = { partial ->
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            if (revision != resolveRevision || partial.isEmpty()) return@withContext
                            sources = partial
                            if (play && !started) {
                                val first = choosePlaybackSource(partial, preferred, chosenLanguage) ?: return@withContext
                                launching = false
                                started = true; episode = playbackTarget; source = first; preferredSource = first
                                currentQuality = "Détection…"; page = Page.PLAYER
                            }
                        }
                    }
                    var found = manager.resolveSources(playbackAnime, playbackTarget, chosenLanguage, refresh, receiveSources)
                    if (found.isEmpty() && !started) {
                        // Detail loading may discover other sites after the user presses Play.
                        if (source == null) detailJob?.join()
                        val enriched = selected
                        val additional = enriched?.let { additionalProviderReferences(playbackAnime, it) }.orEmpty()
                        if (enriched != null && additional.isNotEmpty()) {
                            playbackAnime = enriched
                            found = manager.resolveSources(enriched.copy(references = additional), playbackTarget,
                                chosenLanguage, refresh = true, onPartial = receiveSources)
                        }
                    }
                    found
                }
                if (resolved != null) sources = resolved
                if (sources.isEmpty()) {
                    detailError = "Aucun serveur disponible en $chosenLanguage. Actualisez les sources ou choisissez un autre épisode."
                }
                else if (play && !started) {
                    choosePlaybackSource(sources, preferred, chosenLanguage)?.let { first ->
                        episode = playbackTarget; source = first; preferredSource = first
                        currentQuality = "Détection…"; page = Page.PLAYER
                    }
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { detailError = "La source ne répond pas. ${e.message.orEmpty()}"
            } finally { if (revision == resolveRevision) { streamLoading = false; launching = false } }
        }
    }

    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { query = it; page = Page.SEARCH }
    }
    // Vidrift is an embed: resolve its public API while the viewer is still choosing a
    // server, so the player does not have to spend the whole budget after the tap.
    val prewarmCandidate = sources.firstOrNull { it.mimeType == VIDRIFT_MIME_TYPE }
    LaunchedEffect(prewarmCandidate?.url, page) {
        if (page == Page.DETAIL && prewarmCandidate != null) prewarmPlaybackSource(prewarmCandidate)
    }
    val pairedHost by RemoteLink.target.collectAsState()
    var receivedPlayback by remember { mutableStateOf(false) }
    var receivedQuery by remember { mutableStateOf<String?>(null) }
    var remoteError by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        // Health checks are explicitly requested from Sources, not on every startup.
        runCatching { RemoteLink.start(context) }
        for (command in RemoteLink.commands) {
            if (launching) continue
            try { when (command.action) {
                "key" -> remoteKeyCode(command.query)?.let { code ->
                    if (command.query == "home") { if (page == Page.PLAYER) leavePlayer(); navigate(Page.HOME) }
                    else if (command.query.startsWith("volume_")) {
                        val audio = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                        audio.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC,
                            if (command.query == "volume_up") android.media.AudioManager.ADJUST_RAISE else android.media.AudioManager.ADJUST_LOWER,
                            android.media.AudioManager.FLAG_SHOW_UI)
                    } else {
                        hostActivity?.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, code))
                        hostActivity?.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, code))
                    }
                }
                "search" -> {
                    if (page != Page.PLAYER) navigate(Page.SEARCH)
                    receivedQuery = command.query
                    query = command.query; category = command.category
                }
                "toggle", "seek", "seek_by", "sleep" -> if (page == Page.PLAYER) { RemoteLink.playerCommands.trySend(command); Unit } else Unit
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
            } } catch (e: CancellationException) { throw e }
            catch (_: Exception) { remoteError = "Commande TV impossible. Choisissez un autre lecteur ou relancez la commande." }
        }
    }
    LaunchedEffect(page) {
        if (page == Page.SOURCES && providerNames.isEmpty()) providerNames = manager.getProviderNames()
    }
    DisposableEffect(library) {
        val prefs = context.getSharedPreferences("tvsama_library", 0)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "history" || key == "hidden_resume" || key == "hidden_resume_times") { history = playableHistory(library.continuing()); recommendationHistory = library.history() }
            if (key == "favorites") favorites = playableCards(library.favorites())
            if (key == "search_matches") searchMatches = playableCards(library.searchMatches())
            if (key == "searches") recentSearches = library.searches()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    LaunchedEffect(pairedHost) {
        if (pairedHost != null) while (true) {
            try { RemoteLink.sync(context); history = playableHistory(library.continuing()) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { }
            delay(3000)
        }
    }
    LaunchedEffect(page, query, catalogLoading, catalog) {
        if (page == Page.SEARCH && query.isNotBlank() && !catalogLoading) {
            library.rememberSearchMatches(query, catalog.filter { item ->
                (listOf(item.title) + item.aliases).any { CatalogIdentity.title(it).contains(CatalogIdentity.title(query)) }
            })
        }
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
                page = Page.REMOTE
                Toast.makeText(context, "Lecture lancée sur la télévision", Toast.LENGTH_SHORT).show()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                page = if (selected?.tag == "Direct") returnPage else Page.DETAIL
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
            val key = "$category|$query"
            if (loadedCatalogKey == key && loadedGeneration == generation && catalog.isNotEmpty()) return@LaunchedEffect
            pageJob?.cancel(); loadingMore = false
            catalogLoading = true; catalogError = ""; catalogPage = 1; hasMore = true; pagingError = false
            catalog = emptyList()
            val rankedSearch = query.isNotBlank()
            try {
                if (query.isNotBlank()) { library.rememberSearch(query); recentSearches = library.searches() }
                if (catalogKey != key) { catalog = emptyList(); catalogKey = key }
                
                // We now use onPartial to show results as they arrive, improving perceived speed.
                val result = withTimeoutOrNull(if (rankedSearch) 20_000L else 10_000L) {
                    manager.searchAllProviders(query, category, onPartial = { partial ->
                        val relevant = partial // Relaxed for better discovery, ranking still handles order
                        catalog = withContext(kotlinx.coroutines.Dispatchers.Default) {
                            rankCatalogue(CatalogIdentity.merge(relevant), query)
                        }
                    })
                }.orEmpty()
                
                val finalRelevant = result // Relaxed for better discovery
                catalog = withContext(kotlinx.coroutines.Dispatchers.Default) {
                    rankCatalogue(CatalogIdentity.merge(finalRelevant), query)
                }
                if (catalog.isNotEmpty()) { loadedCatalogKey = key; loadedGeneration = generation }
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
            Page.PLAYER -> leavePlayer()
            Page.DETAIL -> { detailJob?.cancel(); resolveJob?.cancel(); serverPrefetchJob?.cancel(); page = returnPage }
            Page.FAVORITES -> if (showWatched) showWatched = false else navigate(Page.HOME)
            else -> navigate(Page.HOME)
        }
    }
    BackHandler(menuExpanded && page != Page.PLAYER) {
        menuExpanded = false; menuHadFocus = false
        if (context.isTelevision()) scope.launch { withFrameNanos { }; runCatching { menuButtonFocus.requestFocus() } }
    }

    if (unavailableSources.isNotEmpty() && !inPip) AlertDialog(onDismissRequest = { unavailableSources = emptyList() },
        title = { Text("Sources indisponibles") },
        text = { Text(unavailableSources.joinToString("\n")) },
        confirmButton = { TextButton(onClick = { unavailableSources = emptyList(); navigate(Page.SOURCES) }) { Text("Voir les sources") } },
        dismissButton = { TextButton(onClick = { unavailableSources = emptyList() }) { Text("Fermer") } })
    Surface(Modifier.fillMaxSize(), color = if (page == Page.PLAYER) Color.Black else Ink) {
      // Titles follow the preferred version: VF shows French, VOSTFR the original (romanised).
      CompositionLocalProvider(LocalTitleLanguage provides language) {
        if (page == Page.PLAYER && source != null && (pairedHost == null || receivedPlayback)) {
            Column(Modifier.fillMaxSize().then(if (inPip) Modifier else Modifier.safeDrawingPadding())) {
                if (!inPip && !isFullscreen && playerControlsVisible && sources.size > 1) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                    items(sources) { candidate ->
                        val selectedQuality = if (candidate.url == source?.url && currentQuality != "Détection…") currentQuality else candidate.quality
                        Action("${candidate.provider} · $selectedQuality · ${when (candidate.mimeType) { VIDRIFT_MIME_TYPE -> "Lecteur Vidrift"; else -> "${candidate.latencyMs.takeIf { it > 0 } ?: "?"} ms" }}", selected = candidate.url == source?.url) {
                            startPlayback(candidate)
                        }
                    }
                }
                if (!inPip && !isFullscreen && playerError.isNotBlank()) {
                    Text(playerError, color = Color(0xFFFFB4AB), modifier = Modifier.padding(12.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        sources.filter { it.url != source?.url }.forEach { alternative -> Action("${alternative.provider} · ${alternative.name}") { source = alternative; currentQuality = "Détection…"; playerError = "" } }
                        Action("Actualiser") { if (selected?.tag == "Direct") page = returnPage else { page = Page.DETAIL; resolve(false, true) } }
                        Action("Fermer") { playerError = "" }
                    }
                }
                val playingAnime = selected
                val playingEpisode = episode
                val entries = playingAnime?.episodes.orEmpty().distinctBy { it.seasonNumber to it.number }
                val episodeIndex = entries.indexOfFirst { it.number == playingEpisode?.number && it.seasonNumber == playingEpisode?.seasonNumber }
                key(livePlaybackRevision) {
                    val playbackSource = source!!
                    TvSamaPlayer(source = playbackSource, anime = playingAnime, episode = playingEpisode, title = "${playingAnime?.title.orEmpty()}${playingEpisode?.title?.let { " · $it" }.orEmpty()}", poster = playingAnime?.poster.orEmpty(),
                    onBack = { leavePlayer() },
                    imdbId = playingAnime?.imdbId,
                    isMovie = playingAnime?.tag == "Film",
                    seasonNumber = if (playingAnime?.tag == "Film") 0 else playingEpisode?.introSeason ?: playingEpisode?.seasonNumber ?: 0,
                    episodeNumber = if (playingAnime?.tag == "Film") 0 else playingEpisode?.introNumber ?: playingEpisode?.number ?: 0,
                    onPrevious = if (playingAnime?.tag != "Film" && episodeIndex > 0) ({ resolve(true, target = entries[episodeIndex - 1]) }) else null,
                    onNext = if (playingAnime?.tag != "Film" && episodeIndex >= 0 && episodeIndex + 1 < entries.size) ({ resolve(true, target = entries[episodeIndex + 1]) }) else null,
                    onAutoNext = if (autoplay && playingAnime?.tag !in listOf("Film", "Direct") && episodeIndex >= 0 && episodeIndex + 1 < entries.size)
                        ({ resolve(true, target = entries[episodeIndex + 1]) }) else null,
                    resumeAt = playingAnime?.takeUnless { it.isLiveContent() }?.let { library.progress(it, playingEpisode) } ?: 0,
                    subtitlesEnabled = subtitlesEnabled,
                    onActualQuality = { currentQuality = it },
                    onPrefetchNext = if (playingAnime?.tag == "Série" && episodeIndex >= 0 && episodeIndex + 1 < entries.size) ({
                        prefetchJob?.cancel()
                        val chosen = preferredSource ?: source
                        prefetchJob = scope.launch {
                            try { withTimeoutOrNull(15_000) {
                                val next = entries[episodeIndex + 1]
                                val candidates = manager.resolveSources(playingAnime, next, language)
                                choosePlaybackSource(candidates, chosen, language)?.let { PlaybackCache.prefetch(context, it) }
                            } }
                            catch (e: CancellationException) { throw e }
                            catch (_: Exception) { }
                        }
                    }) else null,
                    isFullscreen = isFullscreen,
                    onControlsVisibleChange = { playerControlsVisible = it },
                    onToggleFullscreen = {
                        isFullscreen = !isFullscreen
                        setPlayerFullscreen(hostActivity, isFullscreen)
                    },
                    onProgress = { position, duration -> playingAnime?.takeUnless { it.isLiveContent() }?.let { library.saveAsync(it, playingEpisode, position, duration) } },
                    modifier = Modifier.weight(1f).fillMaxWidth(), onError = playbackError@ { message ->
                        if (source != playbackSource) return@playbackError
                        source?.let { invalidateResolvedStream(it) }
                        playerError = message
                        if (message == SHORT_PROGRAM_ERROR || message == UNKNOWN_PROGRAM_ERROR) {
                            manager.rejectShortStream(playbackSource)
                            sources = sources.filterNot { it.url == playbackSource.url }
                            providerRevision++
                        }
                        if (message == LANGUAGE_MISMATCH_ERROR) {
                            sources = sources.filterNot { it.url == playbackSource.url }
                            providerRevision++
                        }
                        if (message.contains("démarré dans le délai") && playingAnime?.tag != "Direct") {
                            resolveJob?.cancel(); streamLoading = false; launching = false
                            playerError = "Aucun flux natif n’a démarré dans le délai imparti. Choisissez une autre source."
                            leavePlayer()
                            return@playbackError
                        }
                        val failed = playbackSource.url
                        if (playingAnime?.tag == "Direct") {
                            val serverId = source?.serverId?.takeIf { it.isNotBlank() }
                            serverId?.let { failedLiveServers.add(it) }
                            sources = sources.filter { it.url != failed }
                            val alternative = sources.firstOrNull()
                            if (alternative != null) { source = alternative; playerError = "" } else {
                                resolveJob?.cancel()
                                resolveJob = scope.launch {
                                    try {
                                        val next = resolveLiveEvent(context, playingAnime.id, failedLiveServers.toSet())
                                        sources = listOf(next); source = next; livePlaybackRevision++; playerError = ""
                                    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                                        playerError = "Les lecteurs natifs de ce direct sont indisponibles. Actualisez pour réessayer."
                                    }
                                    catch (e: CancellationException) { throw e }
                                    catch (_: Exception) { playerError = "Les lecteurs natifs de ce direct sont indisponibles. Actualisez pour réessayer." }
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
                                        else playerError = if (message in listOf(SHORT_PROGRAM_ERROR, UNKNOWN_PROGRAM_ERROR, LANGUAGE_MISMATCH_ERROR))
                                            "$message Aucun autre serveur confirmé pour ce contenu." else
                                            "Aucun autre serveur lisible pour cet épisode. Choisissez une autre version ou réessayez plus tard."
                                    } catch (e: CancellationException) { throw e }
                                    catch (_: Exception) { playerError = "Les serveurs ne répondent pas. Réessayez plus tard." }
                                }
                            }
                        }
                    },
                    onEnded = {
                        val entries = selected?.episodes.orEmpty().distinctBy { it.seasonNumber to it.number }
                        val index = entries.indexOfFirst { it.number == episode?.number && it.seasonNumber == episode?.seasonNumber }
                        if (playingAnime?.tag !in listOf("Film", "Direct") && autoplay && index >= 0 && index + 1 < entries.size) {
                            resolve(true, target = entries[index + 1])
                        }
                    })
                }
            }
        } else BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val wide = maxWidth >= 840.dp
            val phone = !context.isTelevision() && androidx.compose.ui.platform.LocalConfiguration.current.smallestScreenWidthDp < 600
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().padding(horizontal = if (wide) 24.dp else 16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = if (wide) 16.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Action("☰", Modifier.focusRequester(menuButtonFocus).size(48.dp).semantics { contentDescription = "Menu" }) { menuExpanded = !menuExpanded }
                        Spacer(Modifier.width(if (wide) 20.dp else 8.dp))
                        Column(if (wide) Modifier else Modifier.weight(1f)) {
                            Brand(compact = !wide)
                            Text("Made by Leon <3", color = Color(0xFFFF79B9), fontSize = 10.sp, maxLines = 1)
                        }
                        if (wide) Spacer(Modifier.weight(1f))
                        if (phone) {
                            IconAction(R.drawable.ic_search, "Rechercher", Modifier.size(48.dp)) { navigate(Page.SEARCH) }
                        } else if (wide) {
                            androidx.compose.material3.OutlinedButton(onClick = { navigate(Page.SEARCH) },
                                contentPadding = PaddingValues(horizontal = 10.dp), modifier = Modifier.height(48.dp)) {
                                Icon(painterResource(R.drawable.ic_search), contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Rechercher", maxLines = 1)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        if (pairedHost != null) IconAction(R.drawable.ic_pairing_bolt, "Ouvrir la télécommande", Modifier.size(48.dp), tint = Accent) { navigate(Page.REMOTE) }
                        else CastRouteButton(Modifier.size(48.dp))
                    }
                    if (!wide && !phone) androidx.compose.material3.OutlinedButton(onClick = { navigate(Page.SEARCH) },
                        contentPadding = PaddingValues(horizontal = 10.dp), modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Icon(painterResource(R.drawable.ic_search), contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Rechercher", maxLines = 1)
                    }
                    if (!wide && menuExpanded) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                        items(Page.entries.filter { it != Page.DETAIL && it != Page.PLAYER && it != Page.SEARCH && (it != Page.REMOTE || pairedHost != null) }.withIndex().toList()) { (index, destination) ->
                            Action(destination.label, Modifier.then(if (index == 0) Modifier.focusRequester(menuFocus) else Modifier), selected = page == destination) { navigate(destination); menuExpanded = false }
                        }
                    }

                    when (page) {
                        Page.REMOTE -> RemoteControls { navigate(Page.HOME) }
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
                            CategoryRail(category) { when (it) { "TV" -> navigate(Page.TV); "Diffusions en direct" -> navigate(Page.LIVE); else -> category = it } }
                            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.End) { Action(if (catalogLoading) "Chargement…" else "↻ Actualiser", enabled = !catalogLoading) { generation++ } }
                            if ((catalogLoading && catalog.isEmpty()) || (page == Page.HOME && query.isBlank() && catalog.isNotEmpty() && discoveryCards.isEmpty()))
                                LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color.White)
                            if (catalogError.isNotBlank()) Text(catalogError, color = Muted, modifier = Modifier.padding(vertical = 14.dp))
                            LazyVerticalGrid(columns = GridCells.Adaptive(if (compactPosters) { if (wide) 125.dp else 110.dp } else { if (wide) 175.dp else 130.dp }), state = if (page == Page.HOME) homeGridState else searchGridState,
                                modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
                                if (page == Page.HOME) item(key = "section:featured", contentType = "featured", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                    // Reserve the cinema slot before its network response to keep discoveries still.
                                    Box(Modifier.fillMaxWidth().height(if (wide) 440.dp else 390.dp)) {
                                        if (featured.isNotEmpty()) FeaturedCarousel(featured, wide) { openDetails(it) }
                                        if (cinemaError.isNotBlank()) Column(Modifier.align(Alignment.TopStart).background(Ink.copy(alpha = .94f)).padding(8.dp)) {
                                            Text(cinemaError, color = Coral)
                                            Action("Réessayer les sorties cinéma") { cinemaRevision++ }
                                        } else if (featured.isEmpty()) Column {
                                            Text("Chargement des sorties cinéma…", color = Muted)
                                            LinearProgressIndicator(Modifier.fillMaxWidth())
                                        }
                                    }
                                }
                                if (page == Page.HOME && history.isNotEmpty()) item(key = "section:resume", contentType = "resume", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text("Reprendre", style = MaterialTheme.typography.titleLarge)
                                        LazyRow(state = resumeRowState, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            items(history, key = { cardKey(it.anime) }) { entry -> ResumeCard(entry, onRemove = { library.hideResume(entry.anime); history = playableHistory(library.continuing()) }) { openDetails(entry.anime, entry.episodeId) } }
                                        }
                                    }
                                }
                                item(key = "section:discover", contentType = "section-header", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                    Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)) {
                                        Text(if (page == Page.SEARCH) "${catalog.size} titres" else "À découvrir", style = MaterialTheme.typography.titleLarge)
                                    }
                                }
                                val displayedCards = if (page == Page.HOME && query.isBlank()) {
                                    if (discoveryCards.isNotEmpty()) discoveryCards else catalog
                                } else catalog
                                items(displayedCards.distinctBy(::cardKey), key = ::cardKey, contentType = { "poster" }) { anime -> PosterCard(anime, cardModifier(anime)) {
                                    keyboard?.hide(); focusManager.clearFocus(); openDetails(anime)
                                } }
                                if (catalog.isNotEmpty() && hasMore) item(key = "section:more", contentType = "more", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                    Action(if (loadingMore) "Chargement…" else if (pagingError) "Réessayer le chargement" else "Afficher davantage", enabled = !loadingMore) {
                                        loadMore()
                                    }
                                }
                            }
                        }
                        Page.DETAIL -> selected?.let { anime ->
                            val shownTitle = anime.displayTitle(language)
                            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
                                item { Action("‹ ${returnPage.label}") { page = returnPage; detailJob?.cancel(); resolveJob?.cancel(); serverPrefetchJob?.cancel() } }
                                if (!wide) item {
                                    Artwork(anime.poster.ifBlank { anime.banner }, shownTitle,
                                        Modifier.fillMaxWidth().height(240.dp), crop = false)
                                }
                                item {
                                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                        if (wide) Artwork(anime.poster, shownTitle, Modifier.width(175.dp).height(260.dp))
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Text(anime.tag.uppercase(), color = Accent, fontSize = 12.sp, letterSpacing = 2.sp)
                                            SectionTitle(shownTitle)
                                            Text((listOfNotNull(anime.year?.toString()) + anime.genres).joinToString(" · "), color = Muted)
                                            if (anime.tag == "Série") Text(listOfNotNull(
                                                anime.firstAired?.let { "Première diffusion : $it" },
                                                anime.seriesStatus ?: "Statut non communiqué",
                                                anime.lastAired?.takeIf { anime.seriesStatus in listOf("Terminée", "Annulée") }?.let { "Dernière diffusion : $it" }
                                            ).joinToString(" · "), color = Muted)
                                            anime.metadataUrl?.let { url -> TextButton(onClick = {
                                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                            }) { Text("Métadonnées TVmaze · CC BY-SA", color = Muted) } }
                                            val synopsis = com.streamflixreborn.streamflix.providers.TvSamaAnimeProvider.cleanSynopsis(anime.description)
                                            Text(synopsis.ifBlank { "Le synopsis n'est pas disponible pour ce titre." }, color = Color(0xFFE0E0E0))
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                listOf("VF", "VOSTFR").forEach { version ->
                                                    Action(version, selected = language == version, enabled = !streamLoading) {
                                                        language = version; library.setLanguage(version)
                                                        sources = emptyList(); detailError = ""
                                                    }
                                                }
                                            }
                                            if (anime.provider == "AlloCiné") Text("Sortie cinéma · disponibilité à vérifier dans vos sources.", color = Muted)
                                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Action(if (streamLoading && sources.isEmpty()) "Recherche des serveurs…" else if (anime.provider == "AlloCiné") "Rechercher une source" else if (library.progress(anime, episode) > 0) "▶ Reprendre" else "▶ Regarder", primary = true, enabled = !detailLoading && (!streamLoading || sources.isNotEmpty())) {
                                                    if (sources.isNotEmpty()) { retryPlayback = { resolve(true, true) }
                                                        startPlayback(choosePlaybackSource(sources, preferredSource, language) ?: sources.first())
                                                    }
                                                    else resolve(true, target = episode ?: library.resumeEpisode(anime))
                                                }
                                                val inMyList = favorites.any { LibraryStore.sameFavorite(it, anime) }
                                                Action(if (inMyList) "✓ Retirer de ma liste" else "+ Ma liste") {
                                                    library.setFavorite(anime, !inMyList)
                                                    favorites = playableCards(library.favorites())
                                                }
                                            }
                                        }
                                    }
                                }
                                if (!launching && (detailLoading || streamLoading)) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color.White) }
                                if (detailError.isNotBlank()) item { Text(detailError, color = Color(0xFFFFB4AB)); Action("Réessayer") { if (detailLoading || anime.episodes.isEmpty() && anime.tag != "Film") openDetails(anime) else resolve(true, true) } }
                                if (anime.tag != "Film" && anime.episodes.isNotEmpty()) item {
                                    EpisodePicker(anime, library, episode, onSelect = { chosen -> episode = chosen; sources = emptyList(); detailError = ""; resolve(true, target = chosen) }, reverseLayout = wide && !phone)
                                }
                                item {
                                    Text("Versions et serveurs", style = MaterialTheme.typography.titleLarge)
                                    Text("Les serveurs disponibles sont classés par qualité. Vous gardez le choix.", color = Muted, modifier = Modifier.padding(vertical = 8.dp))
                                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Action("↻ Sources", enabled = !streamLoading && !detailLoading) { resolve(false, true) }
                                    }
                                }
                                items(sources) { video ->
                                    Action(sourceLabel(video, language), Modifier.fillMaxWidth()) { startPlayback(video) }
                                }
                            }
                        }
                        Page.FAVORITES -> {
                            if (showWatched) {
                                val watched = remember(history) { library.completedContent() }
                                SectionTitle("Contenu visionné", "Films terminés et séries dont tous les épisodes connus ont été vus.")
                                Action("← Ma liste") { showWatched = false }
                                if (watched.isEmpty()) EmptyState("Aucun contenu terminé", "Les films et séries terminés apparaîtront ici.")
                                LazyVerticalGrid(GridCells.Adaptive(if (compactPosters) 125.dp else 175.dp), Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                                    items(watched, key = { cardKey(it) }) { anime -> PosterCard(anime) { openDetails(anime) } }
                                }
                            } else {
                                SectionTitle("Ma liste", "Vos films et séries, à retrouver quand vous voulez.")
                                Action("Contenu visionné") { showWatched = true }
                                if (favorites.isEmpty()) EmptyState("Votre collection commence ici", "Ouvrez une fiche puis sélectionnez « + Ma liste ».")
                                LazyVerticalGrid(GridCells.Adaptive(if (compactPosters) 125.dp else 175.dp), Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) { items(favorites) { anime ->
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        PosterCard(anime) { openDetails(anime) }
                                        Action("Retirer", Modifier.fillMaxWidth()) {
                                            library.setFavorite(anime, false)
                                            favorites = playableCards(library.favorites())
                                        }
                                    }
                                } }
                            }
                        }
                        Page.HISTORY -> {
                            SectionTitle("Reprendre", "Retrouvez votre épisode et votre position de lecture.")
                            if (history.isEmpty()) EmptyState("Rien en cours pour le moment", "Vos lectures apparaîtront ici automatiquement.")
                            LazyVerticalGrid(if (wide) GridCells.Adaptive(230.dp) else GridCells.Fixed(2), Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                                items(history, key = { cardKey(it.anime) }) { entry ->
                                    ResumeCard(entry, modifier = Modifier.fillMaxWidth(), onRemove = { library.hideResume(entry.anime); history = playableHistory(library.continuing()) }) { openDetails(entry.anime, entry.episodeId) }
                                }
                            }
                        }
                        Page.TV, Page.LIVE -> key(page) { LiveScreen(tvOnly = page == Page.TV, modifier = Modifier.fillMaxWidth().weight(1f)) { event, videos ->
                            retryPlayback = {
                                resolveJob?.cancel()
                                launching = true
                                resolveJob = scope.launch {
                                    try {
                                        val next = resolveLiveEvent(context, event.url)
                                        sources = listOf(next); source = next; livePlaybackRevision++; page = Page.PLAYER
                                    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                                        playerError = "Le flux direct n’a pas pu être résolu dans le lecteur TvSama."
                                    }
                                    catch (e: CancellationException) { throw e }
                                    catch (_: Exception) {
                                        playerError = "Le flux direct n’a pas pu être résolu dans le lecteur TvSama."
                                    }
                                    finally { launching = false }
                                }
                            }
                            selected = Anime(event.title, "Direct", emptyList(), id = event.url, provider = if (FrenchLiveTv.owns(event.url)) "TVradioZap" else "Streamed")
                            episode = null; sources = videos; source = videos.first(); returnPage = page; page = Page.PLAYER
                        } }
                        Page.SOURCES -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            SectionTitle("Vos sources", "Catalogues intégrés et accès aux sites encore non intégrés.")
                            Action(if (refreshingSources) "Actualisation…" else "↻ Actualiser adresses et catalogues", enabled = !refreshingSources && verifyingSource == null) { refreshSources() }
                            Action("Tester les lecteurs des sources activées", enabled = !refreshingSources && verifyingSource == null) { verifyMedia(null) }
                            if (refreshingSources) { LinearProgressIndicator(Modifier.fillMaxWidth()); Action("Annuler") { refreshJob?.cancel(); refreshingSources = false } }
                            if (verifyingSource != null) {
                                Text("Recherche d’un lecteur sur $verifyingSource… La lecture sera confirmée après lancement.", color = Muted)
                                Action("Annuler le test") { verifySourceJob?.cancel() }
                            }
                            providerNames.forEach { name ->
                                val external = manager.externalSource(name)
                                val enabled = remember(name, providerRevision) { manager.isProviderEnabled(name) }
                                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Column {
                                        val state = manager.sourceHealth()[name]
                                        val verified = enabled && state?.startsWith("✓") == true
                                        Text(name, fontWeight = FontWeight.SemiBold, color = if (verified) SourceGreen else Color.White)
                                        Text(if (!enabled) "Désactivée" else state ?: if (name == "Vidrift") "○ Lecteur officiel intégré · langue et lecture à confirmer" else if (external != null) "○ Site web uniquement · pas de lecteur intégré" else "○ Pas encore testée · choisissez « Tester un lecteur »",
                                            color = when {
                                                !enabled -> Muted
                                                state?.startsWith("✕") == true -> Coral
                                                state?.startsWith("⚠") == true -> Color(0xFFFFC857)
                                                state?.startsWith("✓") == true -> SourceGreen
                                                else -> Muted
                                            },
                                            fontWeight = FontWeight.SemiBold)
                                        Text(manager.accessUrl(name).orEmpty(), color = Muted, fontSize = 12.sp, maxLines = 2)
                                        external?.let {
                                            Text(it.limitation, color = Muted, fontSize = 12.sp, maxLines = 2)
                                        }
                                    }
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Action(if (enabled) "Activé" else "Désactivé", selected = enabled) { manager.setProviderEnabled(name, !enabled); unavailableSources = unavailableSources.filter(manager::isProviderEnabled); providerRevision++; generation++ }
                                        manager.accessUrl(name)?.let { url -> 
                                    val needsValidation = name in setOf("FRAnime", "AnimeKO", "Anime-Sama", "Animes-Sama")
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Action(if (name == "Vidrift") "À propos" else if (external != null) "Catalogue web" else "Accès") { sourceAccessRetry = null; sourceAccess = name to url }
                                        if (needsValidation) {
                                            Action("Valider Captcha", primary = false) { sourceAccessRetry = null; sourceAccess = name to url }
                                        }
                                    }
                                }
                                        if (external == null && enabled) Action(if (verifyingSource == name) "Test du lecteur…" else "Tester un lecteur",
                                            enabled = verifyingSource == null && !refreshingSources) { verifyMedia(name) }
                                    }
                                }
                                HorizontalDivider(color = Line)
                            }
                            SourceDirectoryUi { providerRevision++; generation++ }
                            PersonalSources { custom -> selected = Anime(custom.name, "Flux personnel", emptyList(), id = custom.url); episode = null; source = custom; sources = listOf(custom); page = Page.PLAYER }
                            Spacer(Modifier.height(24.dp))
                        }
                        Page.SETTINGS -> TvSettings(language, { language = it; library.setLanguage(it) }, autoplay, { autoplay = it; library.setAutoplay(it) },
                            historyBackup,
                            { library.clearHistory(); history = playableHistory(library.continuing()); recommendationHistory = library.history(); historyBackup = library.hasHistoryBackup() },
                            { library.restoreHistory(); history = playableHistory(library.continuing()); recommendationHistory = library.history(); historyBackup = library.hasHistoryBackup() })
                        else -> Unit
                    }
                }
                if (wide && menuExpanded) Column(Modifier.align(Alignment.CenterStart).width(220.dp).fillMaxHeight()
                    .background(Ink).border(width = 1.dp, color = Line).onFocusChanged {
                        if (it.hasFocus) menuHadFocus = true else if (menuHadFocus) { menuExpanded = false; menuHadFocus = false }
                    }.focusGroup().padding(start = 24.dp, end = 18.dp, top = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Brand()
                    Spacer(Modifier.height(26.dp))
                    Page.entries.filter { it != Page.DETAIL && it != Page.PLAYER && it != Page.SEARCH && (it != Page.REMOTE || pairedHost != null) }.forEachIndexed { index, destination ->
                        Action(destination.label, Modifier.fillMaxWidth().then(if (index == 0) Modifier.focusRequester(menuFocus) else Modifier), selected = page == destination) { navigate(destination); menuExpanded = false }
                    }
                    Spacer(Modifier.weight(1f))
                    Text("LE CINÉMA, CHEZ VOUS.", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 24.dp))
                }
            }
        }
      }
    }
}

@Composable
private fun Brand(compact: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(painterResource(R.drawable.tvsama_witch), contentDescription = null, tint = Color.Unspecified,
            modifier = Modifier.size(if (compact) 34.dp else 44.dp))
        Text("TvSama", fontWeight = FontWeight.Black, fontSize = if (compact) 20.sp else 25.sp,
            color = Color.White, maxLines = 1)
    }
}

@Composable
private fun FeaturedCarousel(titles: List<Anime>, wide: Boolean, onOpen: (Anime) -> Unit) {
    if (titles.isEmpty()) return
    val pager = rememberPagerState(pageCount = { titles.size })
    val scope = rememberCoroutineScope()
    val motion = MotionEnabled.current
    var moveJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun move(direction: Int) {
        if (titles.size > 1) {
            val next = (pager.targetPage + direction + titles.size) % titles.size
            moveJob?.cancel()
            moveJob = scope.launch {
                if (motion) pager.animateScrollToPage(next, animationSpec = tween(350))
                else pager.scrollToPage(next)
            }
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
            Action("‹", Modifier.semantics { contentDescription = "Affiche précédente" }) { move(-1) }
            Action("›", Modifier.semantics { contentDescription = "Affiche suivante" }) { move(1) }
        }
    }
}

@Composable
private fun Hero(anime: Anime, wide: Boolean, active: Boolean = true, onClick: () -> Unit) {
    val shown = anime.displayTitle(LocalTitleLanguage.current)
    Box(Modifier.fillMaxWidth().height(if (wide) 380.dp else 330.dp)) {
        Artwork(anime.banner.ifBlank { anime.poster }, shown, Modifier.fillMaxSize(), isBanner = true)
        // Keep the artwork readable: darken slightly the left side and the bottom.
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
            0f to Ink.copy(alpha = .45f),
            0.25f to Ink.copy(alpha = .20f),
            0.5f to Color.Transparent
        )))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to Color.Transparent,
            0.65f to Color.Transparent,
            1f to Ink.copy(alpha = .55f)
        )))
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth(.64f).padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // A solid badge stays crisp on top of the artwork instead of a tiny spaced-out
            // label that pixelates and disappears into the banner.
            Box(Modifier.background(Accent, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp)) {
                Text("SORTIE CINÉMA", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            }
            Text(shown, color = Color.White, fontSize = if (wide) 46.sp else 34.sp, lineHeight = if (wide) 50.sp else 38.sp,
                fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text((listOfNotNull(CatalogIdentity.effectiveYear(anime)?.toString(), anime.firstAired?.take(4)) + anime.genres.take(3)).distinct().joinToString(" · "),
                color = Color(0xFFE8E8E8), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Action("Découvrir  →", primary = true, enabled = active, onClick = onClick)
        }
    }
}

@Composable
private fun EpisodePicker(anime: Anime, library: LibraryStore, selected: Episode?, onSelect: (Episode) -> Unit, reverseLayout: Boolean = false) {
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
        LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(12.dp), reverseLayout = reverseLayout) {
            items(seasonEntries) { entry ->
                Column(Modifier.width(225.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(Modifier.fillMaxWidth().height(120.dp)) {
                        Artwork(entry.poster.ifBlank { fallbackPoster }, entry.title, Modifier.fillMaxSize())
                    }
                    Text(if (entry.seasonNumber == 0) "Spécial ${entry.number}" else "S${entry.seasonNumber} · Épisode ${entry.number}", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val progress = library.fraction(anime, entry)
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = Accent)
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
