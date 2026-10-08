package fr.nekotv

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
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

internal data class RemotePlayback(val title: String = "", val position: Long = 0, val duration: Long = 0,
    val playing: Boolean = false, val hasPrevious: Boolean = false, val hasNext: Boolean = false,
    val anime: Anime? = null, val episode: Episode? = null)
internal data class RemoteSnapshot(val playback: RemotePlayback = RemotePlayback(), val history: List<SavedPlayback> = emptyList(), val hidden: Map<String, Long> = emptyMap())

/** Authenticated LAN commands. No discovery, cloud account or Cast receiver is required. */
internal object RemoteLink {
    private const val PORT = 28743
    private const val MAX_FRAME = 1024 * 1024
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val startMutex = Mutex()
    private var server: ServerSocket? = null
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
        val host = NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }.filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }?.hostAddress ?: error("Connectez cet appareil au Wi-Fi local.")
        val secret = token(context)
        if (server == null) {
            val listener = ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(PORT)) }
            server = listener
            scope.launch {
                while (!listener.isClosed) {
                    val socket = try { listener.accept() } catch (_: Exception) { break }
                    // Process sequentially to bound memory and preserve command order.
                    socket.use {
                        runCatching {
                            it.soTimeout = 3000
                            val command = gson.fromJson(readFrame(it), RemoteCommand::class.java)
                            require(command.token == secret)
                            require(command.action in listOf("pair", "search", "play", "preload", "sync", "toggle", "seek", "previous", "next", "episode", "sleep"))
                            if (command.action in listOf("play", "preload")) require(command.sources.isNotEmpty() && command.sources.all { s ->
                                Uri.parse(s.url).scheme in listOf("http", "https")
                            })
                            if (command.action == "sync") {
                                val library = LibraryStore(context)
                                library.mergeHistory(command.history.take(200))
                                library.mergeHiddenResume(command.hidden)
                                writeFrame(it, gson.toJson(RemoteSnapshot(playback, library.history().take(100).map { entry -> entry.copy(anime = entry.anime.copy(episodes = emptyList())) }, library.hiddenResumeTimes())))
                            } else {
                                val accepted = command.action == "pair" || commands.trySend(command).isSuccess
                                writeFrame(it, if (accepted) "OK" else "BUSY")
                            }
                        }
                    }
                }
            }
        }
        Uri.Builder().scheme("tvsama").authority("pair").appendQueryParameter("host", host)
            .appendQueryParameter("token", secret).appendQueryParameter("device", android.os.Build.MODEL).build().toString()
    }
    }
    fun stop() { server?.close(); server = null }
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
        val state = gson.fromJson(reply, RemoteSnapshot::class.java)
        library.mergeHistory(state.history)
        library.mergeHiddenResume(state.hidden)
        remoteState.value = state
    }
    private suspend fun exchange(host: String, command: RemoteCommand) = withContext(Dispatchers.IO) {
        Socket().use {
            it.connect(InetSocketAddress(host, PORT), 3000); it.soTimeout = 4000
            writeFrame(it, gson.toJson(command))
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
