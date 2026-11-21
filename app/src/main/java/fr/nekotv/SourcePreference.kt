package fr.nekotv

/** Keep the viewer's chosen player for the next episode: same provider, same server and
 *  therefore the same subtitle/quality behaviour. Falls back to the ranked list. */
internal fun choosePlaybackSource(sources: List<VideoSource>, preferred: VideoSource?, language: String? = null): VideoSource? {
    if (sources.isEmpty()) return null
    val wanted = language?.takeIf { it.isNotBlank() }
    val usable = sources.filter { wanted == null || it.language == wanted }.ifEmpty { sources }
    if (preferred == null) {
        // Priority: Highest Quality first, then lowest latency.
        return usable.sortedWith(
            compareByDescending<VideoSource> { it.quality.contains("1080") }
                .thenByDescending { it.quality.contains("720") }
                .thenBy { it.latencyMs }
        ).firstOrNull()
    }
    return usable.firstOrNull { it.url == preferred.url }
        ?: usable.firstOrNull {
            it.provider == preferred.provider && it.name == preferred.name &&
                it.serverId == preferred.serverId && it.quality == preferred.quality
        }
        ?: usable.firstOrNull { it.provider == preferred.provider && it.name == preferred.name }
        ?: usable.firstOrNull { it.provider == preferred.provider }
        ?: usable.firstOrNull()
}
