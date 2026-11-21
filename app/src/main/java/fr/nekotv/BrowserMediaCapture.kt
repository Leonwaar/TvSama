package fr.nekotv

import com.streamflixreborn.streamflix.models.Video
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Captures the media request actually displayed by a site page so it can play natively. */
internal fun browserMediaCandidate(address: String, requestHeaders: Map<String, String>, page: String, provider: String): VideoSource? {
    val url = address.toHttpUrlOrNull() ?: return null
    val mime = when {
        url.encodedPath.endsWith(".m3u8", true) -> "application/x-mpegURL"
        url.encodedPath.endsWith(".mpd", true) -> "application/dash+xml"
        url.encodedPath.endsWith(".mp4", true) -> "video/mp4"
        else -> return null
    }
    val headers = requestHeaders.filterKeys { key ->
        listOf("Referer", "Origin", "User-Agent", "Cookie").any { it.equals(key, true) }
    }.toMutableMap()
    if (headers.keys.none { it.equals("Referer", true) }) headers["Referer"] = page
    if (headers.keys.none { it.equals("User-Agent", true) }) headers["User-Agent"] = com.streamflixreborn.streamflix.providers.TvSamaAnimeProvider.USER_AGENT
    return VideoSource("Vidéo affichée", url.toString(), "UNKNOWN", "Auto", provider, headers, mimeType = mime, reachable = false)
}
