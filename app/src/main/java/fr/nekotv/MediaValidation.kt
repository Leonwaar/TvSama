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
        bytes.size >= 4 && bytes.take(4) == listOf(0x1a.toByte(), 0x45.toByte(), 0xdf.toByte(), 0xa3.toByte()) -> "video/x-matroska"
        bytes.size >= 12 && bytes.copyOfRange(4, 8).toString(Charsets.US_ASCII) in listOf("ftyp", "styp", "moov", "moof") -> "video/mp4"
        bytes.size > 376 && bytes[0] == 0x47.toByte() && bytes[188] == 0x47.toByte() && bytes[376] == 0x47.toByte() -> "video/mp2t"
        else -> null
    }
}

internal fun hlsVodDurationMs(playlist: String): Long? {
    val lines = playlist.lineSequence().map(String::trim).toList()
    if (lines.firstOrNull()?.trimStart('\uFEFF') != "#EXTM3U" || "#EXT-X-ENDLIST" !in lines) return null
    var totalSeconds = 0.0
    var segments = 0
    for (line in lines) if (line.startsWith("#EXTINF:")) {
        val seconds = line.substringAfter(':').substringBefore(',').toDoubleOrNull() ?: return null
        if (!seconds.isFinite() || seconds <= 0) return null
        totalSeconds += seconds
        segments++
    }
    return if (segments > 0) (totalSeconds * 1000).toLong() else null
}

internal suspend fun validateLiveSource(source: VideoSource, minimumVodDurationMs: Long = 0, verifyLanguage: Boolean = false,
    onFailure: (String) -> Unit = {}): VideoSource? = withContext(Dispatchers.IO) {
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
        var quality = source.quality
        if (type == "application/x-mpegURL") {
            var address = resolved
            var playlist = bytes.toString(Charsets.UTF_8)
            // Keep the master so declared audio/subtitle renditions can contradict the label.
            val master = playlist
            // Replace "Auto"/"HD" by the real resolution when the manifest declares it.
            if (quality in listOf("Auto", "HD", "")) declaredHlsQuality(master)?.let { quality = it }
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
            hlsVodDurationMs(playlist)?.let { duration ->
                check(duration >= minimumVodDurationMs) { "Extrait HLS trop court pour le programme" }
            }
            val segment = media.segment ?: error("Manifeste HLS sans segment")
            media.initialization?.let { check(mediaMimeType(sample(it, 4096).second) == "video/mp4") { "Initialisation MP4 invalide" } }
            val data = sample(segment, 4096).second
            if (media.key != null) {
                check(sample(media.key, 17).second.size == 16) { "Clé AES HLS invalide" }
                val text = data.toString(Charsets.UTF_8).trimStart()
                check(data.isNotEmpty() && !text.startsWith("<") && data.size % 16 == 0) { "Segment chiffré invalide" }
            } else check(mediaMimeType(data) in listOf("video/mp2t", "video/mp4") ||
                data.size > 2 && data[0] == 0xff.toByte() && data[1].toInt() and 0xf6 == 0xf0) { "Conteneur de segment invalide" }
            if (verifyLanguage && source.language in listOf("VF", "VOSTFR")) {
                val info = parseHlsLanguageInfo(master)
                if (info != null) {
                    check(audioMatchesRequested(source.language, source.originalLanguage, info.audio)) {
                        "Piste audio non conforme à la version demandée"
                    }
                    val frenchSidecar = source.subtitles.any { it.language.lowercase() in listOf("fr", "fra", "fre") }
                    check(subtitlesMatchRequested(source.language, info.subtitles) || frenchSidecar) {
                        "Sous-titres français absents du manifeste"
                    }
                }
            }
        }
        source.copy(mimeType = type, reachable = true, quality = quality)
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
    catch (e: java.io.IOException) { onFailure(e.javaClass.simpleName + ": " + e.message.orEmpty().replace(Regex("https?://\\S+"), "[adresse]")); null }
    catch (e: IllegalStateException) { onFailure(e.message.orEmpty().replace(Regex("https?://\\S+"), "[adresse]")); null }
    catch (e: IllegalArgumentException) { onFailure(e.message.orEmpty().replace(Regex("https?://\\S+"), "[adresse]")); null }
}

internal data class HlsProbe(val variant: String?, val segment: String?, val initialization: String?, val key: String?)

internal data class HlsLanguageInfo(val audio: List<String>, val subtitles: List<String>)

/** Normalises an ISO language declaration ("fr-FR", "fre", "fra") to its primary tag. */
internal fun mediaLanguageTag(raw: String?): String? {
    val value = raw?.trim()?.lowercase()?.takeIf { it.isNotBlank() && it != "und" } ?: return null
    return when (val primary = value.substringBefore('-').substringBefore('_')) {
        "fra", "fre", "français", "francais" -> "fr"
        "eng" -> "en"; "jpn" -> "ja"; "kor" -> "ko"; "zho", "chi", "cmn", "yue" -> "zh"
        "spa" -> "es"; "deu", "ger" -> "de"; "ita" -> "it"
        "por" -> "pt"; "rus" -> "ru"; "ara" -> "ar"; "hin" -> "hi"; "tam" -> "ta"; "tel" -> "te"
        else -> primary
    }
}

private val renditionLanguages = mapOf(
    Regex("(?i)\\b(fr|fra|fre|french|français|francais|vf|vff|vfq|vostfr)\\b") to "fr",
    Regex("(?i)\\b(en|eng|english|anglais)\\b") to "en",
    Regex("(?i)\\b(ja|jpn|japanese|japonais)\\b") to "ja",
    Regex("(?i)\\b(ko|kor|korean|coréen|coreen)\\b") to "ko",
    Regex("(?i)\\b(zh|zho|chi|chinese|chinois|mandarin|cantonese)\\b") to "zh",
    Regex("(?i)\\b(es|spa|spanish|espagnol|espanol|castellano|latino)\\b") to "es",
    Regex("(?i)\\b(de|deu|ger|german|deutsch|allemand)\\b") to "de",
    Regex("(?i)\\b(it|ita|italian|italiano|italien)\\b") to "it",
    Regex("(?i)\\b(pt|por|portuguese|portugais)\\b") to "pt",
    Regex("(?i)\\b(ru|rus|russian|russe)\\b") to "ru",
    Regex("(?i)\\b(ar|ara|arabic|arabe)\\b") to "ar"
)

/** Reads the language renditions declared by an HLS master playlist. */
internal fun parseHlsLanguageInfo(playlist: String): HlsLanguageInfo? {
    val text = playlist.trimStart('\uFEFF', ' ', '\r', '\n', '\t')
    if (!text.startsWith("#EXTM3U")) return null
    val audio = mutableListOf<String>()
    val subtitles = mutableListOf<String>()
    for (line in text.lineSequence()) {
        if (!line.trimStart().startsWith("#EXT-X-MEDIA", true)) continue
        val type = Regex("(?i)TYPE=([A-Z]+)").find(line)?.groupValues?.get(1)?.uppercase() ?: continue
        val declared = Regex("(?i)LANGUAGE=\"([^\"]+)\"").find(line)?.groupValues?.get(1)
        val language = mediaLanguageTag(declared)
            ?: renditionLanguages.entries.filter { it.key.containsMatchIn(line) }.map { it.value }.singleOrNull()
            ?: continue
        when (type) {
            "AUDIO" -> audio += language
            "SUBTITLES" -> subtitles += language
        }
    }
    return HlsLanguageInfo(audio.distinct(), subtitles.distinct())
}

internal suspend fun hlsLanguageInfo(source: VideoSource): HlsLanguageInfo? = withContext(Dispatchers.IO) {
    try {
        val request = okhttp3.Request.Builder().url(source.url)
            .header("User-Agent", com.streamflixreborn.streamflix.providers.TvSamaAnimeProvider.USER_AGENT)
            .apply { source.headers.forEach { (key, value) -> header(key, value) } }
            .build()
        MediaNetwork.probeClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val body = response.body ?: return@withContext null
            val buffered = body.source()
            buffered.request(300_000)
            parseHlsLanguageInfo(buffered.buffer.clone().readUtf8())
        }
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
    catch (_: Exception) { null }
}

/** VF requires declared French audio; VOSTFR requires the original audio and no fully French dub. */
internal fun audioMatchesRequested(requested: String, original: String?, languages: List<String>): Boolean {
    val tags = languages.mapNotNull(::mediaLanguageTag).distinct()
    if (tags.isEmpty()) return true
    return when (requested) {
        "VF" -> "fr" in tags
        "VOSTFR" -> {
            val known = original?.trim()?.lowercase()?.takeIf { it.matches(Regex("[a-z]{2,3}")) }
            if (known != null && known != "fr") known in tags else tags.any { it != "fr" }
        }
        else -> false
    }
}

internal fun subtitlesMatchRequested(requested: String, languages: List<String>): Boolean {
    if (requested != "VOSTFR") return true
    val tags = languages.mapNotNull(::mediaLanguageTag)
    return tags.isEmpty() || "fr" in tags
}

/** Confirms the manifest exposes a French subtitle rendition that ExoPlayer can select. */
internal suspend fun hlsDeclaresFrenchSubtitles(source: VideoSource): Boolean =
    hlsLanguageInfo(source)?.subtitles?.contains("fr") == true

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
