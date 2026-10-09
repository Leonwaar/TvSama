package fr.nekotv

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.cast.MediaTrack
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.common.images.WebImage
import kotlinx.coroutines.delay
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

private fun VideoSource.contentType(): String? = mimeType ?: when {
    Uri.parse(url).path.orEmpty().endsWith(".m3u8", true) -> MimeTypes.APPLICATION_M3U8
    Uri.parse(url).path.orEmpty().endsWith(".mpd", true) -> MimeTypes.APPLICATION_MPD
    Uri.parse(url).path.orEmpty().endsWith(".mp4", true) -> MimeTypes.VIDEO_MP4
    else -> null
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun TvSamaPlayer(
    source: VideoSource,
    anime: Anime? = null,
    episode: Episode? = null,
    title: String,
    poster: String = "",
    resumeAt: Long = 0,
    subtitlesEnabled: Boolean = true,
    isFullscreen: Boolean = false,
    onControlsVisibleChange: (Boolean) -> Unit = {},
    onToggleFullscreen: () -> Unit = {},
    onBack: () -> Unit = {},
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    onAutoNext: (() -> Unit)? = null,
    imdbId: String? = null,
    isMovie: Boolean = false,
    seasonNumber: Int = 0,
    episodeNumber: Int = 0,
    onActualQuality: (String) -> Unit = {},
    onProgress: (Long, Long) -> Unit,
    modifier: Modifier = Modifier,
    onError: (String) -> Unit = {},
    onEnded: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val inPip = (context as? MainActivity)?.inPictureInPicture == true
    val errorCallback by rememberUpdatedState(onError)
    val endedCallback by rememberUpdatedState(onEnded)
    val previousCallback by rememberUpdatedState(onPrevious)
    val nextCallback by rememberUpdatedState(onNext)
    val autoNextCallback by rememberUpdatedState(onAutoNext)
    val advance = remember(anime?.id, episode?.id, seasonNumber, episodeNumber) { EpisodeAdvance() }
    val qualityCallback by rememberUpdatedState(onActualQuality)
    val controlsCallback by rememberUpdatedState(onControlsVisibleChange)
    var casting by remember(source) { mutableStateOf(false) }
    var playing by remember(source) { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var segments by remember(imdbId, seasonNumber, episodeNumber, isMovie) { mutableStateOf<EpisodeSegments?>(null) }
    var playbackDuration by remember(source) { mutableLongStateOf(0L) }
    var position by remember(source) { mutableLongStateOf(0L) }
    LaunchedEffect(imdbId, seasonNumber, episodeNumber, isMovie) { segments = fetchSegments(imdbId, seasonNumber, episodeNumber, isMovie) }
    var castMessage by remember(source) { mutableStateOf<String?>(null) }
    val castContext = remember(context) { castContextOrNull(context) }
    val loadStartedAt = remember(source) { android.os.SystemClock.elapsedRealtime() }
    val timer = remember(context) { SleepTimer(context) }
    // A new lecture starts a fresh countdown using the choice saved in Settings.
    // This also clears an expired deadline when the timer was left enabled.
    val initialSleepDeadline = remember(source) { timer.restart() }
    var sleepDeadline by remember(source) { mutableLongStateOf(initialSleepDeadline) }
    val player = remember(source) {
        val dataSource = if (anime?.tag == "Direct") MediaNetwork.factory(source)
            else PlaybackCache.factory(context, source)
        ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(dataSource)).apply {
            if (anime?.tag == "Direct") setLoadControl(androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(2_000, 10_000, 500, 1_000)
                .setPrioritizeTimeOverSizeThresholds(true).build())
        }.build().apply {
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setPreferredAudioLanguages(*(if (source.language == "VOSTFR") arrayOf("ja", "en") else arrayOf("fr", "fra")))
                .setPreferredTextLanguage("fr")
                .setSelectUndeterminedTextLanguage(source.language == "VOSTFR").build()
            setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, true)
            setHandleAudioBecomingNoisy(true)
            setMediaItem(MediaItem.Builder().setUri(source.url).apply {
                setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(title)
                    .apply { if (poster.startsWith("https://")) setArtworkUri(Uri.parse(poster)) }.build())
                source.contentType()?.let { setMimeType(it) }
                setSubtitleConfigurations(source.subtitles.map { subtitle ->
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.url))
                        .setMimeType(subtitle.mimeType).setLanguage(subtitle.language).setLabel(subtitle.label)
                        .setSelectionFlags(if (source.language == "VOSTFR") C.SELECTION_FLAG_DEFAULT else 0).build()
                })
            }.build())
            if (anime?.tag == "Direct") seekToDefaultPosition() else seekTo(resumeAt.coerceAtLeast(0))
            prepare()
            playWhenReady = initialSleepDeadline == 0L || initialSleepDeadline > System.currentTimeMillis()
        }
    }
    SideEffect { (context as? MainActivity)?.pictureInPictureEligible = playing && !casting }
    val progressCallback = remember(player, advance) {
        { position: Long, duration: Long ->
            onProgress(if (advance.triggered && duration > 0) duration else position, duration)
        }
    }
    var sleepRemaining by remember { mutableLongStateOf(0L) }
    var sleepExpired by remember { mutableStateOf(false) }
    var sleepMenu by remember { mutableStateOf(false) }
    var pairingMenu by remember { mutableStateOf(false) }
    var dimmed by remember { mutableStateOf(false) }
    val dimPreferences = remember(context) { context.getSharedPreferences("tvsama_settings", 0) }
    var autoDim by remember { mutableStateOf(dimPreferences.getBoolean("pause_dimming", true)) }
    var playerView by remember(player) { mutableStateOf<PlayerView?>(null) }
    BackHandler(!inPip && (sleepMenu || controlsVisible)) {
        when {
            sleepMenu -> sleepMenu = false
            else -> playerView?.hideController()
        }
    }
    var pauseDimmed by remember(player) { mutableStateOf(false) }
    val pauseDimming = remember(player) { PauseDimming() }
    val activity = context as? MainActivity
    val lastInteraction = activity?.lastInteraction ?: 0L
    // Restart the deadline on user input, never on playback-position updates.
    // Keep an open menu available long enough to choose a duration or scan its QR.
    LaunchedEffect(playerView, sleepMenu, pairingMenu, lastInteraction) {
        playerView?.controllerShowTimeoutMs = if (sleepMenu || pairingMenu) 0 else 8_000
    }
    DisposableEffect(activity, player) {
        val wake: () -> Boolean = { pauseDimmed.also { if (it) pauseDimmed = false } }
        activity?.wakePausedScreen = wake
        onDispose { activity?.let { if (it.wakePausedScreen === wake) it.wakePausedScreen = null } }
    }
    LaunchedEffect(player, autoDim, lastInteraction, inPip) {
        pauseDimmed = false
        while (true) {
            val remote = castContext?.sessionManager?.currentCastSession?.remoteMediaClient
            val paused = if (casting) remote?.isPaused == true else !player.playWhenReady && player.playbackState == Player.STATE_READY
            pauseDimmed = pauseDimming.shouldDim(paused, autoDim && !inPip, activity?.lastInteraction ?: lastInteraction)
            delay(250)
        }
    }
    fun setSleep(minutes: Int) {
        timer.configure(minutes)
        sleepDeadline = timer.begin()
        sleepExpired = false
        dimmed = false
        sleepMenu = false
    }
    LaunchedEffect(player) {
        for (command in RemoteLink.playerCommands) {
            if (activity?.notifyPlayerInteraction() == true) continue
            val remote = castContext?.sessionManager?.currentCastSession?.remoteMediaClient
                ?.takeIf { casting && it.mediaInfo?.contentId == source.url }
            // A disconnected Cast session must not start a second, local audio stream.
            if (casting && remote == null) continue
            when (command.action) {
                "toggle" -> {
                    val isPlaying = remote?.isPlaying ?: player.playWhenReady
                    if (isPlaying) {
                        if (remote != null) remote.pause() else player.pause()
                    } else {
                        if (timer.expired()) { sleepDeadline = timer.restart(); sleepExpired = false; dimmed = false }
                        if (remote != null) remote.play() else player.play()
                    }
                }
                "seek" -> {
                    val duration = remote?.streamDuration ?: player.duration
                    val target = command.position.coerceIn(0, duration.takeIf { it > 0 } ?: Long.MAX_VALUE)
                    if (remote != null) remote.seek(MediaSeekOptions.Builder().setPosition(target).build())
                    else player.seekTo(target)
                }
                "sleep" -> { setSleep(180); if (remote != null) remote.play() else player.play() }
            }
        }
    }
    LaunchedEffect(player, sleepDeadline) {
        if (sleepDeadline == 0L) return@LaunchedEffect
        while (System.currentTimeMillis() < sleepDeadline) {
            sleepRemaining = sleepDeadline - System.currentTimeMillis()
            delay(minOf(1_000L, sleepRemaining.coerceAtLeast(1)))
        }
        sleepRemaining = 0
        player.pause()
        castContext?.sessionManager?.currentCastSession?.remoteMediaClient?.pause()
        sleepExpired = true
        dimmed = true
    }
    val darkened = dimmed || pauseDimmed
    DisposableEffect(darkened, context) {
        val window = (context as? android.app.Activity)?.window
        val previous = window?.attributes?.screenBrightness ?: -1f
        // Force the lowest brightness supported by the window while dimmed.
        if (darkened && window != null) window.attributes = window.attributes.apply { screenBrightness = 0f }
        onDispose { if (darkened && window != null) window.attributes = window.attributes.apply { screenBrightness = previous } }
    }
    LaunchedEffect(player, subtitlesEnabled) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesEnabled).build()
    }
    LaunchedEffect(player, casting) {
        while (true) {
            // Keep the Compose toolbar in sync with Media3's actual state,
            // including the intermediate states of its hide/show animations.
            // Reuse the existing player ticker rather than start another timer.
            val visible = playerView?.isControllerFullyVisible == true
            if (controlsVisible != visible) {
                controlsVisible = visible
                controlsCallback(visible)
            }
            val remote = castContext?.sessionManager?.currentCastSession?.remoteMediaClient
            if (casting && remote?.mediaInfo?.contentId == source.url) {
                position = remote.approximateStreamPosition
                playbackDuration = remote.streamDuration
            } else {
                position = player.currentPosition
                playbackDuration = player.duration
            }
            if (advance.claim(position, playbackDuration, segments?.outro,
                    eligible = autoNextCallback != null && !isMovie && anime?.tag != "Direct" &&
                        !timer.expired() && !sleepExpired && (if (casting) remote?.isPlaying == true else player.isPlaying))) {
                // Stop the old stream and record the episode as completed before resolving the next one.
                if (casting) remote?.pause() else { player.pause(); player.seekTo(playbackDuration) }
                progressCallback(playbackDuration, playbackDuration)
                autoNextCallback?.invoke()
            }
            RemoteLink.playback = RemotePlayback(title, position.coerceAtLeast(0), playbackDuration.coerceAtLeast(0),
                if (casting) remote?.isPlaying == true else playing, previousCallback != null, nextCallback != null, anime, episode)
            delay(250)
        }
    }
    DisposableEffect(player, lifecycle) {
        val mediaSession = androidx.media3.session.MediaSession.Builder(context, player)
            .setCallback(object : androidx.media3.session.MediaSession.Callback {
                override fun onPlayerCommandRequest(session: androidx.media3.session.MediaSession,
                    controller: androidx.media3.session.MediaSession.ControllerInfo, command: Int): Int {
                    if (command in setOf(Player.COMMAND_PLAY_PAUSE, Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                            Player.COMMAND_SEEK_BACK, Player.COMMAND_SEEK_FORWARD)) {
                        if (activity?.notifyPlayerInteraction() == true) return androidx.media3.session.SessionResult.RESULT_ERROR_INVALID_STATE
                        if (command == Player.COMMAND_PLAY_PAUSE && sleepExpired) {
                            sleepDeadline = timer.restart(); sleepExpired = false; dimmed = false
                        }
                    }
                    return androidx.media3.session.SessionResult.RESULT_SUCCESS
                }
            }).build()
        var liveWindowRecoveries = 0
        var lastLiveWindowRecovery = 0L
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                if (BuildConfig.DEBUG) android.util.Log.d("TvSamaPlayer", "Première image ${source.provider} : ${android.os.SystemClock.elapsedRealtime() - loadStartedAt} ms ; ${player.videoFormat?.width}x${player.videoFormat?.height} ; audio ${player.audioFormat?.sampleMimeType.orEmpty()}")
            }
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                if (BuildConfig.DEBUG) {
                    val selected = tracks.groups.flatMap { group -> (0 until group.length).filter(group::isTrackSelected).map { group.getTrackFormat(it) } }
                    android.util.Log.d("TvSamaPlayer", "Pistes actives ${source.provider} : ${selected.joinToString { "${it.sampleMimeType} ${it.language.orEmpty()} ${it.width}x${it.height}" }}")
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                if (BuildConfig.DEBUG) android.util.Log.w("TvSamaPlayer", "Lecture ${source.provider} : ${error.errorCodeName} (${error.cause?.javaClass?.simpleName.orEmpty()})")
                if (anime?.tag == "Direct" && error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (now - lastLiveWindowRecovery > 60_000) liveWindowRecoveries = 0
                    if (liveWindowRecoveries++ < 2) {
                        lastLiveWindowRecovery = now
                        val resume = player.playWhenReady && !casting && !timer.expired()
                        player.seekToDefaultPosition()
                        player.prepare()
                        player.playWhenReady = resume
                        return
                    }
                }
                errorCallback("Ce serveur ne peut pas être lu (${error.errorCodeName}). Essayez un autre serveur.")
            }
            override fun onPlaybackStateChanged(state: Int) {
                playing = player.isPlaying
                if (state == Player.STATE_ENDED && !casting && !timer.expired() && !advance.triggered) {
                    if (anime?.tag == "Direct") castMessage = "La diffusion est terminée."
                    else { progressCallback(player.duration.coerceAtLeast(0), player.duration.coerceAtLeast(0)); endedCallback() }
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                qualityCallback(actualQuality(videoSize.width, videoSize.height))
            }
        }
        player.addListener(listener)
        var resumeOnStart = false
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                resumeOnStart = player.playWhenReady
                progressCallback(player.currentPosition.coerceAtLeast(0), player.duration.coerceAtLeast(0))
                // A visible PiP activity remains STARTED; STOP means it was hidden or dismissed.
                player.pause()
            } else if (event == Lifecycle.Event.ON_START && resumeOnStart && !casting && !timer.expired()) player.play()
        }
        lifecycle.addObserver(observer)
        onDispose {
            progressCallback(player.currentPosition.coerceAtLeast(0), player.duration.coerceAtLeast(0))
            lifecycle.removeObserver(observer)
            player.removeListener(listener)
            (context as? MainActivity)?.pictureInPictureEligible = false
            RemoteLink.playback = RemotePlayback()
            mediaSession.release()
            player.release()
        }
    }
    DisposableEffect(player, castContext) {
        var active = true
        var trackedRemote: RemoteMediaClient? = null
        var lastRemotePosition = resumeAt.coerceAtLeast(0)
        var lastRemoteDuration = 0L
        var remoteFinished = false
        var remoteWantedPlayback = player.playWhenReady
        fun saveRemote() {
            trackedRemote?.let { remote ->
                if (remote.mediaInfo?.contentId == source.url) {
                    lastRemotePosition = remote.approximateStreamPosition.coerceAtLeast(0)
                    lastRemoteDuration = remote.streamDuration.coerceAtLeast(0)
                    when (remote.playerState) {
                        MediaStatus.PLAYER_STATE_PLAYING, MediaStatus.PLAYER_STATE_BUFFERING -> remoteWantedPlayback = true
                        MediaStatus.PLAYER_STATE_PAUSED -> remoteWantedPlayback = false
                        MediaStatus.PLAYER_STATE_IDLE -> if (remote.mediaStatus?.idleReason == MediaStatus.IDLE_REASON_FINISHED) remoteWantedPlayback = false
                    }
                    progressCallback(lastRemotePosition, lastRemoteDuration)
                }
            }
        }
        val remoteCallback = object : RemoteMediaClient.Callback() {
            override fun onStatusUpdated() {
                if (!active || !casting) return
                val remote = trackedRemote ?: return
                val status = remote.mediaStatus ?: return
                if (remote.mediaInfo?.contentId != source.url) return
                saveRemote()
                if (status.playerState == MediaStatus.PLAYER_STATE_IDLE && status.idleReason == MediaStatus.IDLE_REASON_FINISHED && !remoteFinished) {
                    remoteFinished = true
                    if (!timer.expired() && !advance.triggered) endedCallback()
                } else if (status.playerState == MediaStatus.PLAYER_STATE_IDLE && status.idleReason == MediaStatus.IDLE_REASON_ERROR) {
                    castMessage = "La télévision ne peut pas lire ce serveur. Choisissez une autre source."
                }
            }
        }
        fun track(remote: RemoteMediaClient) {
            if (trackedRemote === remote) return
            trackedRemote?.unregisterCallback(remoteCallback)
            trackedRemote = remote
            remote.registerCallback(remoteCallback)
        }
        fun resumeLocal() {
            if (!casting) return
            saveRemote()
            casting = false
            player.seekTo(lastRemotePosition)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && remoteWantedPlayback && !timer.expired()) player.play()
        }
        fun sendTo(session: CastSession) {
            // The default receiver cannot attach arbitrary Referer/Cookie headers to requests.
            if (source.headers.isNotEmpty()) {
                castMessage = "Ce serveur exige des en-têtes privés et ne peut pas être casté. Choisissez un autre serveur."
                return
            }
            val remote = session.remoteMediaClient ?: return
            track(remote)
            if (remote.mediaInfo?.contentId == source.url && remote.playerState != MediaStatus.PLAYER_STATE_IDLE) {
                casting = true
                player.pause()
                if (timer.expired()) remote.pause()
                saveRemote()
                player.seekTo(lastRemotePosition)
                return
            }
            val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
                putString(MediaMetadata.KEY_TITLE, title)
                if (poster.startsWith("http")) addImage(WebImage(Uri.parse(poster)))
            }
            val info = MediaInfo.Builder(source.url)
                .setContentType(source.contentType() ?: "video/mp4")
                .setStreamType(if (anime?.tag == "Direct") MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED).setMetadata(metadata)
                .setMediaTracks(source.subtitles.mapIndexed { index, subtitle ->
                    MediaTrack.Builder(index.toLong() + 1, MediaTrack.TYPE_TEXT)
                        .setSubtype(MediaTrack.SUBTYPE_SUBTITLES).setContentId(subtitle.url)
                        .setContentType(subtitle.mimeType).setLanguage(subtitle.language).setName(subtitle.label).build()
                }).build()
            val request = MediaLoadRequestData.Builder().setMediaInfo(info)
                .setCurrentTime(player.currentPosition.coerceAtLeast(0)).setAutoplay(player.playWhenReady && !timer.expired())
            if (source.language == "VOSTFR" && source.subtitles.isNotEmpty()) request.setActiveTrackIds(longArrayOf(1))
            remote.load(request.build()).setResultCallback { result ->
                if (!active) return@setResultCallback
                if (result.status.isSuccess) {
                    casting = true
                    remoteFinished = false
                    castMessage = null
                    player.pause()
                    if (timer.expired()) remote.pause()
                } else {
                    castMessage = "La télévision n'a pas pu lire ce serveur. Essayez une autre source."
                }
            }
        }
        val listener = object : SessionManagerListener<CastSession> {
            override fun onSessionStarted(session: CastSession, id: String) = sendTo(session)
            override fun onSessionResumed(session: CastSession, suspended: Boolean) {
                val remote = session.remoteMediaClient
                if (remote?.mediaInfo?.contentId == source.url) {
                    track(remote)
                    casting = true
                    player.pause()
                    if (timer.expired()) remote.pause()
                    saveRemote()
                    player.seekTo(lastRemotePosition)
                }
            }
            override fun onSessionEnded(session: CastSession, error: Int) { resumeLocal() }
            override fun onSessionSuspended(session: CastSession, reason: Int) {
                saveRemote()
                castMessage = "Connexion à la télévision interrompue. Reconnexion en cours…"
            }
            override fun onSessionStarting(session: CastSession) {}
            override fun onSessionStartFailed(session: CastSession, error: Int) { castMessage = "Connexion à la télévision impossible." }
            override fun onSessionEnding(session: CastSession) { saveRemote() }
            override fun onSessionResuming(session: CastSession, id: String) {}
            override fun onSessionResumeFailed(session: CastSession, error: Int) { resumeLocal() }
        }
        castContext?.sessionManager?.addSessionManagerListener(listener, CastSession::class.java)
        castContext?.sessionManager?.currentCastSession?.takeIf { it.isConnected }?.let { sendTo(it) }
        onDispose {
            active = false
            if (casting) saveRemote()
            trackedRemote?.unregisterCallback(remoteCallback)
            castContext?.sessionManager?.removeSessionManagerListener(listener, CastSession::class.java)
        }
    }
    LaunchedEffect(player) {
        while (true) {
            delay(3000)
            val remote = castContext?.sessionManager?.currentCastSession?.remoteMediaClient
            if (casting && remote != null) {
                progressCallback(remote.approximateStreamPosition.coerceAtLeast(0), remote.streamDuration.coerceAtLeast(0))
                player.seekTo(remote.approximateStreamPosition.coerceAtLeast(0))
            } else progressCallback(player.currentPosition.coerceAtLeast(0), player.duration.coerceAtLeast(0))
        }
    }
    Box(modifier.background(Color.Black)) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = {
            TvPlayerView(it).apply {
                playerView = this
                this.player = player
                useController = true
                controllerAutoShow = false
                controllerShowTimeoutMs = 8_000
                setControllerAnimationEnabled(false)
                addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                    if (android.os.Build.VERSION.SDK_INT >= 26 && context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                        val bounds = android.graphics.Rect()
                        if (view.getGlobalVisibleRect(bounds)) runCatching {
                            (context as? android.app.Activity)?.setPictureInPictureParams(
                                android.app.PictureInPictureParams.Builder().setSourceRectHint(bounds).build())
                        }
                    }
                }
                setShowSubtitleButton(true)
                setShowPreviousButton(false)
                setShowNextButton(false)
                findViewById<android.widget.LinearLayout>(androidx.media3.ui.R.id.exo_time)?.let { timeBar ->
                    fun episodeButton(tagName: String, description: String, icon: Int, click: () -> Unit) {
                        timeBar.addView(android.widget.ImageButton(context).apply {
                            tag = tagName; contentDescription = description
                            setImageResource(icon); setColorFilter(android.graphics.Color.WHITE)
                            setBackgroundResource(R.drawable.player_control_focus)
                            isFocusable = true
                            setOnClickListener { click() }
                        }, android.widget.LinearLayout.LayoutParams((44 * resources.displayMetrics.density).toInt(), (44 * resources.displayMetrics.density).toInt()))
                    }
                    episodeButton("episode_previous", "Épisode précédent", androidx.media3.ui.R.drawable.exo_icon_previous) { previousCallback?.invoke() }
                    episodeButton("episode_next", "Épisode suivant", androidx.media3.ui.R.drawable.exo_icon_next) { nextCallback?.invoke() }
                }
                findViewById<androidx.media3.ui.DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress)?.apply {
                    setBackgroundResource(R.drawable.player_control_focus)
                    setKeyTimeIncrement(5_000)
                    setOnKeyListener { _, code, event ->
                        if (code == android.view.KeyEvent.KEYCODE_DPAD_LEFT || code == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                            setKeyTimeIncrement(seekIncrement(event.eventTime - event.downTime))
                        }
                        false
                    }
                }
                fun highlightControls(view: android.view.View) {
                    if (view !== this && view.isClickable && view.isFocusable) view.setBackgroundResource(R.drawable.player_control_focus)
                    if (view is android.view.ViewGroup) for (index in 0 until view.childCount) highlightControls(view.getChildAt(index))
                }
                highlightControls(this)
                var lastTap = 0L
                var seekTarget = 0L
                var tapDirection = 0
                var lastSeekPlayer: Player? = null
                fun seekTap(event: android.view.MotionEvent) {
                    // AndroidView survives a server change; use the player currently attached to it.
                    val activePlayer = this.player ?: return
                    val now = android.os.SystemClock.elapsedRealtime()
                    val direction = if (event.x < width / 2) -1 else 1
                    val base = if (lastSeekPlayer === activePlayer && now - lastTap < 900 && direction == tapDirection) seekTarget else activePlayer.currentPosition
                    seekTarget = (base + direction * 15_000L).coerceIn(0, activePlayer.duration.takeIf { it > 0 } ?: Long.MAX_VALUE)
                    lastTap = now; tapDirection = direction
                    lastSeekPlayer = activePlayer
                    activePlayer.seekTo(seekTarget)
                }
                val gestures = android.view.GestureDetector(context, object : android.view.GestureDetector.SimpleOnGestureListener() {
                    override fun onDown(event: android.view.MotionEvent) = true
                    override fun onSingleTapUp(event: android.view.MotionEvent): Boolean {
                        if (android.os.SystemClock.elapsedRealtime() - lastTap < 900) { seekTap(event); return true }
                        return false
                    }
                    override fun onDoubleTap(event: android.view.MotionEvent): Boolean {
                        seekTap(event)
                        return true
                    }
                })
                setOnTouchListener { _, event -> gestures.onTouchEvent(event); false }
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                keepScreenOn = true
                isFocusable = true
                requestFocus()
                showController()
            }
        }, update = { view ->
            if (playerView !== view) playerView = view
            view.player = player; view.keepScreenOn = playing && !casting
            view.useController = !inPip
            if (inPip) view.hideController()
            view.findViewWithTag<android.widget.ImageButton>("episode_previous")?.apply {
                isEnabled = onPrevious != null; alpha = if (isEnabled) 1f else .35f
            }
            view.findViewWithTag<android.widget.ImageButton>("episode_next")?.apply {
                isEnabled = onNext != null; alpha = if (isEnabled) 1f else .35f
            }
        })
        if (controlsVisible && !inPip) Row(Modifier.align(Alignment.TopEnd).fillMaxWidth().padding(14.dp)
            .horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            Action("Retour", onClick = onBack)
            Box {
                IconAction(R.drawable.ic_sleep_timer, "Minuterie de veille") { sleepMenu = true }
                androidx.compose.material3.DropdownMenu(expanded = sleepMenu, onDismissRequest = { sleepMenu = false }) {
                    SleepTimer.options.forEach { minutes ->
                        androidx.compose.material3.DropdownMenuItem(text = { Text(if (minutes < 60) "$minutes min" else "${minutes / 60} h${if (minutes % 60 != 0) " ${minutes % 60} min" else ""}") }, onClick = { setSleep(minutes) })
                    }
                    androidx.compose.material3.DropdownMenuItem(text = { Text("Désactiver") }, onClick = { setSleep(0) })
                }
            }
            if (sleepDeadline > 0L && sleepRemaining > 0L) {
                Text(
                    text = "${(sleepRemaining / 60_000L) + 1} min",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
            CastRouteButton(Modifier.size(48.dp), onDialogVisibilityChange = { pairingMenu = it })
            if (android.os.Build.VERSION.SDK_INT >= 26 && context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                androidx.compose.material3.IconButton(onClick = { (context as? android.app.Activity)?.enterPictureInPictureMode(android.app.PictureInPictureParams.Builder().setAspectRatio(android.util.Rational(16, 9)).build()) }) {
                    androidx.compose.material3.Icon(painterResource(R.drawable.ic_pip), contentDescription = "Image dans l’image", tint = Color.White)
                }
            }
            Action(if (isFullscreen) "×" else "⛶") { onToggleFullscreen() }
        }
        if (casting) Column(Modifier.fillMaxSize().background(Color.Black), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("Lecture sur votre télévision", color = Color.White)
            Text(title, color = Color.White, modifier = Modifier.padding(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Action("−30 s") {
                    castContext?.sessionManager?.currentCastSession?.remoteMediaClient?.let { remote ->
                        remote.seek(MediaSeekOptions.Builder().setPosition((remote.approximateStreamPosition - 30000).coerceAtLeast(0)).build())
                    }
                }
                androidx.compose.material3.IconButton(onClick = { castContext?.sessionManager?.currentCastSession?.remoteMediaClient?.togglePlayback() }, modifier = Modifier.size(72.dp)) {
                    androidx.compose.material3.Icon(painterResource(R.drawable.ic_kiki_play), contentDescription = "Lecture / Pause", tint = Color.Unspecified)
                }
                Action("+30 s") {
                    castContext?.sessionManager?.currentCastSession?.remoteMediaClient?.let { remote ->
                        val position = (remote.approximateStreamPosition + 30000).let { if (remote.streamDuration > 0) it.coerceAtMost(remote.streamDuration) else it }
                        remote.seek(MediaSeekOptions.Builder().setPosition(position).build())
                    }
                }
                CastRouteButton(Modifier.size(48.dp))
            }
        }
        val activeSegment = listOf("Passer l’intro" to segments?.intro, "Passer l’outro" to segments?.outro)
            .firstOrNull { (_, segment) -> segment != null && position >= segment.start && position < segment.end && (playbackDuration <= 0 || segment.start < playbackDuration) }
        if (!inPip && activeSegment != null) {
            val segment = activeSegment.second!!
            val skipFocus = remember { FocusRequester() }
            LaunchedEffect(activeSegment.first, segment.start) { if (context.isTelevision()) skipFocus.requestFocus() }
            Action(activeSegment.first, Modifier.focusRequester(skipFocus).align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = if (controlsVisible && !casting) 88.dp else 20.dp)
                .background(Color.Black.copy(alpha = .6f))) {
                if (casting) castContext?.sessionManager?.currentCastSession?.remoteMediaClient?.seek(
                    MediaSeekOptions.Builder().setPosition(segment.end).build())
                else player.seekTo(segment.end)
            }
        }
        // Opaque veil: the paused video and source controls must not remain visible.
        if (pauseDimmed && !dimmed && !inPip) Box(Modifier.fillMaxSize().background(Color.Black))
        if (dimmed && !inPip) Column(
            Modifier.fillMaxSize().background(Color.Black),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Minuteur terminé", color = Color.White)
            Action("Reprendre la lecture") {
                val remote = castContext?.sessionManager?.currentCastSession?.remoteMediaClient
                    ?.takeIf { casting && it.mediaInfo?.contentId == source.url }
                if (!casting || remote != null) {
                    sleepDeadline = timer.restart(); sleepExpired = false; dimmed = false
                    if (remote != null) remote.play() else player.play()
                }
            }
        }
        castMessage?.let { Text(it, color = Color.White, modifier = Modifier.align(Alignment.TopCenter).background(Color.Black).padding(16.dp)) }
    }
}

internal fun seekIncrement(heldMillis: Long): Long = when {
    heldMillis < 3000 -> 1000L
    heldMillis < 6000 -> 10000L
    heldMillis < 10000 -> 30000L
    else -> 60000L
}
