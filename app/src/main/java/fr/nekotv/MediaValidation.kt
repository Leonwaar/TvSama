package fr.nekotv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.TimeUnit

/** An embed page is never a video, even if an extractor labels it as HLS. */
internal fun mediaMimeType(bytes: ByteArray): String? {
    val text = bytes.toString(Charsets.UTF_8).trimStart('\uFEFF', ' ', '\r', '\n', '\t')
    return when {
        text.startsWith("#EXTM3U") -> "application/x-mpegURL"
        text.contains("<MPD") && !text.contains("<html", true) -> "application/dash+xml"
        bytes.size >= 12 && bytes.copyOfRange(4, 8).toString(Charsets.US_ASCII) in listOf("ftyp", "styp", "moov", "moof") -> "video/mp4"
        bytes.size > 376 && bytes[0] == 0x47.toByte() && bytes[188] == 0x47.toByte() && bytes[376] == 0x47.toByte() -> "video/mp2t"
        else -> null
    }
}

internal suspend fun validateLiveSource(source: VideoSource, onFailure: (String) -> Unit = {}): VideoSource? = withContext(Dispatchers.IO) {
    try {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12)
        suspend fun sample(address: String, limit: Int): Pair<String, ByteArray> {
            currentCoroutineContext().ensureActive()
            val remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())
            check(remaining > 0) { "Délai de validation du flux dépassé" }
            val originalHost = source.url.toHttpUrl().host
            val request = okhttp3.Request.Builder().url(address)
            .header("User-Agent", com.streamflixreborn.streamflix.providers.TvSamaAnimeProvider.USER_AGENT)
            .apply { source.headers.forEach { (key, value) ->
                if (address.toHttpUrl().host == originalHost || !key.equals("Cookie", true) && !key.equals("Authorization", true)) header(key, value)
            } }.header("Range", "bytes=0-${limit - 1}").build()
            val call = MediaNetwork.probeClient.newCall(request)
            call.timeout().timeout(minOf(remaining, 5_000), TimeUnit.MILLISECONDS)
            return call.execute().use { response ->
                check(response.isSuccessful) { "Flux HTTP ${response.code}" }
                val body = response.body ?: error("Flux vide")
                val bytes = body.byteStream().use { input ->
                    val buffer = ByteArray(limit)
                    var count = 0
                    while (count < buffer.size) {
                        val read = input.read(buffer, count, buffer.size - count)
                        if (read < 0) break
                        count += read
                        // Binary media needs only a signature; playlist references need the complete sample.
                        val type = mediaMimeType(buffer.copyOf(count))
                        if (type != null && type != "application/x-mpegURL") break
                    }
                    buffer.copyOf(count)
                }
                response.request.url.toString() to bytes
            }
        }
        val (resolved, bytes) = sample(source.url, 65_536)
        val type = mediaMimeType(bytes) ?: return@withContext null
        if (type == "application/x-mpegURL") {
            var address = resolved
            var playlist = bytes.toString(Charsets.UTF_8)
            repeat(3) {
                val next = hlsProbe(playlist, address)
                if (next.variant != null) {
                    val fetched = sample(next.variant, 65_536)
                    check(mediaMimeType(fetched.second) == type) { "Variante HLS invalide" }
                    address = fetched.first; playlist = fetched.second.toString(Charsets.UTF_8)
                }
            }
            val media = hlsProbe(playlist, address)
            check(media.variant == null) { "Manifeste HLS récursif" }
            val segment = media.segment ?: error("Manifeste HLS sans segment")
            media.initialization?.let { check(mediaMimeType(sample(it, 4096).second) == "video/mp4") { "Initialisation MP4 invalide" } }
            val data = sample(segment, 4096).second
            if (media.key != null) {
                check(sample(media.key, 17).second.size == 16) { "Clé AES HLS invalide" }
                val text = data.toString(Charsets.UTF_8).trimStart()
                check(data.isNotEmpty() && !text.startsWith("<") && data.size % 16 == 0) { "Segment chiffré invalide" }
            } else check(mediaMimeType(data) in listOf("video/mp2t", "video/mp4") ||
                data.size > 2 && data[0] == 0xff.toByte() && data[1].toInt() and 0xf6 == 0xf0) { "Conteneur de segment invalide" }
        }
        source.copy(mimeType = type)
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
    catch (e: java.io.IOException) { onFailure(e.javaClass.simpleName + ": " + e.message.orEmpty().replace(Regex("https?://\\S+"), "[adresse]")); null }
    catch (e: IllegalStateException) { onFailure(e.message.orEmpty().replace(Regex("https?://\\S+"), "[adresse]")); null }
    catch (e: IllegalArgumentException) { onFailure(e.message.orEmpty().replace(Regex("https?://\\S+"), "[adresse]")); null }
}

internal data class HlsProbe(val variant: String?, val segment: String?, val initialization: String?, val key: String?)

/** Select only published playlist/segment URIs, never an ad URL or an empty manifest. */
internal fun hlsProbe(playlist: String, address: String): HlsProbe {
    val lines = playlist.trimStart('\uFEFF').lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
    check(lines.firstOrNull() == "#EXTM3U") { "Manifeste HLS absent" }
    fun uri(value: String) = address.toHttpUrl().resolve(value)?.takeIf { it.isHttps || it.scheme == "http" }?.toString()
        ?: error("URI HLS invalide")
    fun attribute(line: String, name: String) = Regex("(?:^|[:,])$name=\"([^\"]+)\"").find(line)?.groupValues?.get(1)
    val variantIndex = lines.indexOfFirst { it.startsWith("#EXT-X-STREAM-INF:") }
    val variant = if (variantIndex >= 0) lines.drop(variantIndex + 1).firstOrNull { !it.startsWith('#') }?.let(::uri) else null
    check(variantIndex < 0 || variant != null) { "Variante HLS vide" }
    val segmentIndex = lines.indexOfFirst { it.startsWith("#EXTINF:") }
    val segment = if (segmentIndex >= 0) lines.drop(segmentIndex + 1).firstOrNull { !it.startsWith('#') }?.let(::uri) else null
    val initialization = lines.firstOrNull { it.startsWith("#EXT-X-MAP:") }?.let { attribute(it, "URI") }?.let(::uri)
    val keyLine = lines.take(if (segmentIndex >= 0) segmentIndex + 1 else lines.size).lastOrNull { it.startsWith("#EXT-X-KEY:") }
    val key = if (keyLine != null && !keyLine.contains("METHOD=NONE")) {
        check(keyLine.contains("METHOD=AES-128") && (!keyLine.contains("KEYFORMAT=") || keyLine.contains("KEYFORMAT=\"identity\""))) { "Chiffrement HLS non pris en charge" }
        attribute(keyLine, "URI")?.let(::uri) ?: error("URI de clé HLS absente")
    } else null
    return HlsProbe(variant, segment, initialization, key)
}
