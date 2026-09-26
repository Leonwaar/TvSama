package fr.nekotv

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.media3.datasource.DefaultHttpDataSource
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
    title: String,
    poster: String = "",
    resumeAt: Long = 0,
    subtitlesEnabled: Boolean = true,
    isFullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {},
    onActualQuality: (String) -> Unit = {},
    onProgress: (Long, Long) -> Unit,
    modifier: Modifier = Modifier,
    onError: (String) -> Unit = {},
    onEnded: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val progressCallback by rememberUpdatedState(onProgress)
    val errorCallback by rememberUpdatedState(onError)
    val endedCallback by rememberUpdatedState(onEnded)
    val qualityCallback by rememberUpdatedState(onActualQuality)
    var casting by remember(source) { mutableStateOf(false) }
    var playing by remember(source) { mutableStateOf(false) }
    var castMessage by remember(source) { mutableStateOf<String?>(null) }
    val castContext = remember(context) { castContextOrNull(context) }
    val player = remember(source) {
        val http = DefaultHttpDataSource.Factory().setDefaultRequestProperties(source.headers)
            .setConnectTimeoutMs(15000).setReadTimeoutMs(20000)
        ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(http)).build().apply {
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setPreferredAudioLanguages(*(if (source.language == "VOSTFR") arrayOf("ja", "en") else arrayOf("fr", "fra")))
                .setPreferredTextLanguage("fr")
                .setSelectUndeterminedTextLanguage(source.language == "VOSTFR").build()
            setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, true)
            setHandleAudioBecomingNoisy(true)
            setMediaItem(MediaItem.Builder().setUri(source.url).apply {
                source.contentType()?.let { setMimeType(it) }
                setSubtitleConfigurations(source.subtitles.map { subtitle ->
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.url))
                        .setMimeType(subtitle.mimeType).setLanguage(subtitle.language).setLabel(subtitle.label)
                        .setSelectionFlags(if (source.language == "VOSTFR") C.SELECTION_FLAG_DEFAULT else 0).build()
                })
            }.build())
            seekTo(resumeAt.coerceAtLeast(0))
            prepare()
            playWhenReady = true
        }
    }
    LaunchedEffect(player, subtitlesEnabled) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesEnabled).build()
    }
    DisposableEffect(player, lifecycle) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                errorCallback("Ce serveur ne peut pas être lu (${error.errorCodeName}). Essayez un autre serveur.")
            }
            override fun onPlaybackStateChanged(state: Int) {
                playing = player.isPlaying
                if (state == Player.STATE_ENDED && !casting) endedCallback()
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
                player.pause()
            } else if (event == Lifecycle.Event.ON_START && resumeOnStart && !casting) player.play()
        }
        lifecycle.addObserver(observer)
        onDispose {
            progressCallback(player.currentPosition.coerceAtLeast(0), player.duration.coerceAtLeast(0))
            lifecycle.removeObserver(observer)
            player.removeListener(listener)
            player.release()
        }
    }
    DisposableEffect(player, castContext) {
        var active = true
        var trackedRemote: RemoteMediaClient? = null
        var lastRemotePosition = resumeAt.coerceAtLeast(0)
        var lastRemoteDuration = 0L
        var remoteFinished = false
        fun saveRemote() {
            trackedRemote?.let { remote ->
                if (remote.mediaInfo?.contentId == source.url) {
                    lastRemotePosition = remote.approximateStreamPosition.coerceAtLeast(0)
                    lastRemoteDuration = remote.streamDuration.coerceAtLeast(0)
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
                    endedCallback()
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
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) player.play()
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
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED).setMetadata(metadata)
                .setMediaTracks(source.subtitles.mapIndexed { index, subtitle ->
                    MediaTrack.Builder(index.toLong() + 1, MediaTrack.TYPE_TEXT)
                        .setSubtype(MediaTrack.SUBTYPE_SUBTITLES).setContentId(subtitle.url)
                        .setContentType(subtitle.mimeType).setLanguage(subtitle.language).setName(subtitle.label).build()
                }).build()
            val request = MediaLoadRequestData.Builder().setMediaInfo(info)
                .setCurrentTime(player.currentPosition.coerceAtLeast(0)).setAutoplay(true)
            if (source.language == "VOSTFR" && source.subtitles.isNotEmpty()) request.setActiveTrackIds(longArrayOf(1))
            remote.load(request.build()).setResultCallback { result ->
                if (!active) return@setResultCallback
                if (result.status.isSuccess) {
                    casting = true
                    remoteFinished = false
                    castMessage = null
                    player.pause()
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
            PlayerView(it).apply {
                this.player = player
                useController = true
                setShowSubtitleButton(true)
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                keepScreenOn = true
                isFocusable = true
                requestFocus()
            }
        }, update = { it.player = player; it.keepScreenOn = !casting })
        Action(if (isFullscreen) "×" else "⛶", Modifier.align(Alignment.TopEnd).padding(14.dp)) { onToggleFullscreen() }
        if (!playing && !casting) {
            androidx.compose.material3.IconButton(
                onClick = { player.play() },
                modifier = Modifier.align(Alignment.Center).size(96.dp)
            ) {
                androidx.compose.material3.Icon(
                    painterResource(R.drawable.ic_kiki_play),
                    contentDescription = "Lire",
                    tint = Color.Unspecified,
                    modifier = Modifier.fillMaxSize().padding(8.dp)
                )
            }
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
        castMessage?.let { Text(it, color = Color.White, modifier = Modifier.align(Alignment.TopCenter).background(Color.Black).padding(16.dp)) }
    }
}

private fun actualQuality(width: Int, height: Int): String = when {
    width >= 3840 || height >= 2160 -> "4K"
    width >= 1920 || height >= 1080 -> "1080p"
    width >= 1280 || height >= 720 -> "720p"
    width >= 854 || height >= 480 -> "480p"
    width > 0 || height > 0 -> "${height}p"
    else -> "Détection…"
}
