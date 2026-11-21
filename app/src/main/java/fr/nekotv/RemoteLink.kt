package fr.nekotv

import android.content.Context
import android.net.Uri
import com.google.gson.GsonBuilder
import com.google.gson.InstanceCreator
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID

internal data class RemoteCommand(val token: String = "", val action: String = "", val query: String = "",
    val category: String = "Tous", val anime: Anime? = null, val episode: Episode? = null,
    val sources: List<VideoSource> = emptyList(), val language: String = "VF", val position: Long = 0,
    val history: List<SavedPlayback> = emptyList(), val hidden: Map<String, Long> = emptyMap())
internal fun RemoteCommand.sleepMinutes() = position.toInt().takeIf { it in SleepTimer.options } ?: 30

internal data class RemotePlayback(val title: String = "", val position: Long = 0, val duration: Long = 0,
    val playing: Boolean = false, val hasPrevious: Boolean = false, val hasNext: Boolean = false,
    val anime: Anime? = null, val episode: Episode? = null)
internal data class RemoteSnapshot(val playback: RemotePlayback = RemotePlayback(), val history: List<SavedPlayback> = emptyList(), val hidden: Map<String, Long> = emptyMap())

/** Authenticated LAN commands. No discovery, cloud account or Cast receiver is required. */
internal object RemoteLink {
    private const val PORT = 28743
    private const val MAX_FRAME = 1024 * 1024
    private val gson = GsonBuilder()
        .registerTypeAdapter(Anime::class.java, InstanceCreator { Anime("", "", emptyList()) })
        .registerTypeAdapter(Episode::class.java, InstanceCreator { Episode("", "", emptyList()) })
        .registerTypeAdapter(VideoSource::class.java, InstanceCreator { VideoSource("", "", "UNKNOWN", "Auto", "") })
        .create()

    internal fun decodeCommand(raw: String): RemoteCommand = gson.fromJson(raw, RemoteCommand::class.java).also { command ->
        requireNotNull(command); requireNotNull(command.token); requireNotNull(command.action)
        requireNotNull(command.query); requireNotNull(command.category); requireNotNull(command.language)
        requireNotNull(command.sources).forEach(::validateSource)
        requireNotNull(command.history).forEach(::validateHistory)
        requireNotNull(command.hidden).forEach { (key, value) -> requireNotNull(key); requireNotNull(value) }
        command.anime?.let(::validateAnime); command.episode?.let(::validateEpisode)
        require(command.action !in listOf("play", "preload") || command.anime != null && command.sources.isNotEmpty())
    }
    internal fun decodeSnapshot(raw: String): RemoteSnapshot = gson.fromJson(raw, RemoteSnapshot::class.java).also { state ->
        requireNotNull(state)
        val playback = requireNotNull(state.playback)
        requireNotNull(playback.title); playback.anime?.let(::validateAnime); playback.episode?.let(::validateEpisode)
        requireNotNull(state.history).forEach(::validateHistory)
        requireNotNull(state.hidden).forEach { (key, value) -> requireNotNull(key); requireNotNull(value) }
    }
    private fun validateHistory(entry: SavedPlayback) {
        requireNotNull(entry); validateAnime(requireNotNull(entry.anime))
        requireNotNull(entry.episodeId); requireNotNull(entry.episodeTitle)
        require(entry.position >= 0 && entry.duration > 0)
    }
    private fun validateAnime(anime: Anime) {
        requireNotNull(anime.title); requireNotNull(anime.tag); requireNotNull(anime.id)
        requireNotNull(anime.poster); requireNotNull(anime.banner); requireNotNull(anime.provider); requireNotNull(anime.description)
        requireNotNull(anime.episodes).forEach(::validateEpisode)
        requireNotNull(anime.aliases).forEach { requireNotNull(it) }
        requireNotNull(anime.genres).forEach { requireNotNull(it) }
        requireNotNull(anime.references).forEach { requireNotNull(it); requireNotNull(it.id); requireNotNull(it.provider); requireNotNull(it.tag) }
    }
    private fun validateEpisode(episode: Episode) {
        requireNotNull(episode); requireNotNull(episode.id); requireNotNull(episode.title)
        requireNotNull(episode.description); requireNotNull(episode.poster); requireNotNull(episode.language)
        requireNotNull(episode.sources).forEach(::validateSource)
    }
    private fun validateSource(source: VideoSource) {
        requireNotNull(source); requireNotNull(source.name); requireNotNull(source.provider)
        requireNotNull(source.language); requireNotNull(source.quality); requireNotNull(source.serverId)
        require(Uri.parse(requireNotNull(source.url)).scheme in listOf("http", "https"))
        requireNotNull(source.headers).forEach { (key, value) -> requireNotNull(key); requireNotNull(value) }
        requireNotNull(source.subtitles).forEach { subtitle ->
            requireNotNull(subtitle); requireNotNull(subtitle.url); requireNotNull(subtitle.language)
            requireNotNull(subtitle.label); requireNotNull(subtitle.mimeType)
        }
    }
    private fun compact(anime: Anime) = anime.copy(description = anime.description.take(2000), episodes = anime.episodes.map {
        it.copy(description = "", sources = emptyList())
    })
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val startMutex = Mutex()
    @Volatile private var server: ServerSocket? = null
    private val activeSocket = java.util.concurrent.atomic.AtomicReference<Socket?>()
    val commands = Channel<RemoteCommand>(16)
    val playerCommands = Channel<RemoteCommand>(16)
    @Volatile var playback = RemotePlayback()
    private val remoteState = MutableStateFlow(RemoteSnapshot())
    val snapshot = remoteState.asStateFlow()
    private val targetState = MutableStateFlow<String?>(null)
    val target = targetState.asStateFlow()
    private fun prefs(context: Context) = context.getSharedPreferences("tvsama_settings", 0)
    fun token(context: Context): String = prefs(context).let { p ->
        p.getString("pairing_token", null) ?: UUID.randomUUID().toString().replace("-", "").also {
            p.edit().putString("pairing_token", it).apply()
        }
    }
    fun restore(context: Context) { targetState.value = prefs(context).getString("paired_tv_host", null) }
    fun disconnect(context: Context) {
        prefs(context).edit().remove("paired_tv_host").remove("paired_tv_token").apply()
        targetState.value = null
        remoteState.value = RemoteSnapshot()
    }
    suspend fun start(context: Context): String = withContext(Dispatchers.IO) { startMutex.withLock {
        val application = context.applicationContext
        val host = NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }.filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }?.hostAddress ?: error("Connectez cet appareil au Wi-Fi local.")
        val secret = token(application)
        if (server == null) {
            val listener = ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(PORT)) }
            server = listener
            scope.launch {
                while (!listener.isClosed) {
                    val socket = try { listener.accept() } catch (_: Exception) { break }
                    activeSocket.set(socket)
                    // Process sequentially to bound memory and preserve command order.
                    socket.use {
                        runCatching {
                            it.soTimeout = 3000
                            val command = decodeCommand(readFrame(it))
                            startMutex.withLock {
                                require(server === listener && command.token == secret)
                                require(command.action in listOf("pair", "search", "play", "preload", "sync", "key", "toggle", "seek", "seek_by", "previous", "next", "episode", "sleep"))
                                if (command.action == "key") require(remoteKeyCode(command.query) != null)
                                if (command.action == "seek_by") require(command.position in -120_000..120_000)
                                if (command.action in listOf("play", "preload")) require(command.sources.isNotEmpty() && command.sources.all { s ->
                                    Uri.parse(s.url).scheme in listOf("http", "https")
                                })
                                if (command.action == "sync") {
                                    val library = LibraryStore(application)
                                    library.mergeHistory(command.history.take(200))
                                    library.mergeHiddenResume(command.hidden)
                                    writeFrame(it, gson.toJson(RemoteSnapshot(playback.copy(anime = playback.anime?.let(::compact)), library.history().take(100).map { entry -> entry.copy(anime = entry.anime.copy(episodes = emptyList())) }, library.hiddenResumeTimes())))
                                } else {
                                    val accepted = command.action == "pair" || commands.trySend(command).isSuccess
                                    writeFrame(it, if (accepted) "OK" else "BUSY")
                                }
                            }
                        }
                    }
                    activeSocket.compareAndSet(socket, null)
                }
            }
        }
        Uri.Builder().scheme("tvsama").authority("pair").appendQueryParameter("host", host)
            .appendQueryParameter("token", secret).appendQueryParameter("device", android.os.Build.MODEL).build().toString()
    }
    }
    fun stop() { val listener = server; server = null; listener?.close(); activeSocket.getAndSet(null)?.close() }
    suspend fun resetPreferences(context: Context) {
        withContext(Dispatchers.IO) { startMutex.withLock {
            stop()
            // Explicit defaults also notify observers on Android versions before R.
            check(prefs(context).edit().clear().putBoolean("oled", false).putBoolean("motion", true)
                .putString("density", "Confort").putBoolean("subtitles", true).commit()) { "Réinitialisation impossible" }
            targetState.value = null
            remoteState.value = RemoteSnapshot()
            while (commands.tryReceive().isSuccess) { }
            while (playerCommands.tryReceive().isSuccess) { }
            token(context)
        } }
        try { start(context) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { /* Pairing can restart the listener when Wi-Fi returns. */ }
    }
    suspend fun pair(context: Context, raw: String) {
        val uri = Uri.parse(raw)
        val host = uri.getQueryParameter("host").orEmpty()
        val secret = uri.getQueryParameter("token").orEmpty()
        require(uri.scheme == "tvsama" && uri.host == "pair" && secret.matches(Regex("[a-zA-Z0-9]{12,64}"))) { "QR code TvSama invalide." }
        require(host.matches(Regex("[0-9.]{7,15}")) && java.net.InetAddress.getByName(host).isSiteLocalAddress) { "Ce QR code doit être renouvelé sur la télévision." }
        exchange(host, RemoteCommand(secret, "pair"))
        prefs(context).edit().putString("paired_tv_host", host).putString("paired_tv_token", secret).apply()
        targetState.value = host
    }
    suspend fun send(context: Context, command: RemoteCommand) {
        val host = target.value ?: error("Aucun appareil associé.")
        exchange(host, command.copy(token = prefs(context).getString("paired_tv_token", "").orEmpty()))
    }
    suspend fun sync(context: Context) = withContext(Dispatchers.IO) {
        val host = target.value ?: return@withContext
        val library = LibraryStore(context)
        val reply = exchange(host, RemoteCommand(token = prefs(context).getString("paired_tv_token", "").orEmpty(),
            action = "sync", history = library.history().take(100).map { entry -> entry.copy(anime = entry.anime.copy(episodes = emptyList())) }, hidden = library.hiddenResumeTimes()))
        val state = decodeSnapshot(reply)
        library.mergeHistory(state.history)
        library.mergeHiddenResume(state.hidden)
        remoteState.value = state
    }
    private suspend fun exchange(host: String, command: RemoteCommand) = withContext(Dispatchers.IO) {
        Socket().use {
            it.connect(InetSocketAddress(host, PORT), 3000); it.soTimeout = 4000
            writeFrame(it, gson.toJson(command.copy(anime = command.anime?.let(::compact))))
            val reply = readFrame(it)
            if (command.action != "sync") check(reply == "OK") { "La télévision est occupée. Réessayez." }
            reply
        }
    }
    private fun readFrame(socket: Socket): String {
        val input = DataInputStream(socket.getInputStream())
        val size = input.readInt(); require(size in 1..MAX_FRAME)
        return ByteArray(size).also { input.readFully(it) }.toString(Charsets.UTF_8)
    }
    private fun writeFrame(socket: Socket, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8); require(bytes.size in 1..MAX_FRAME)
        DataOutputStream(socket.getOutputStream()).apply { writeInt(bytes.size); write(bytes); flush() }
    }
}

internal fun remoteKeyCode(key: String): Int? = when (key) {
    "up" -> android.view.KeyEvent.KEYCODE_DPAD_UP
    "down" -> android.view.KeyEvent.KEYCODE_DPAD_DOWN
    "left" -> android.view.KeyEvent.KEYCODE_DPAD_LEFT
    "right" -> android.view.KeyEvent.KEYCODE_DPAD_RIGHT
    "ok" -> android.view.KeyEvent.KEYCODE_DPAD_CENTER
    "back" -> android.view.KeyEvent.KEYCODE_BACK
    "home" -> android.view.KeyEvent.KEYCODE_HOME
    "volume_up" -> android.view.KeyEvent.KEYCODE_VOLUME_UP
    "volume_down" -> android.view.KeyEvent.KEYCODE_VOLUME_DOWN
    else -> null
}
