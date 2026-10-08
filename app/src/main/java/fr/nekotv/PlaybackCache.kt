package fr.nekotv

import android.content.Context
import android.net.Uri
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DataSourceInputStream
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal object PlaybackCache {
    @Volatile private var cache: SimpleCache? = null
    private fun cache(context: Context): SimpleCache = cache ?: synchronized(this) {
        cache ?: SimpleCache(java.io.File(context.applicationContext.cacheDir, "episode-buffer"),
            LeastRecentlyUsedCacheEvictor(24L * 1024 * 1024), StandaloneDatabaseProvider(context.applicationContext))
            .also { cache = it }
    }
    fun factory(context: Context, source: VideoSource): CacheDataSource.Factory = CacheDataSource.Factory()
        .setCache(cache(context)).setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        .setUpstreamDataSourceFactory(MediaNetwork.factory(source))

    suspend fun prefetch(context: Context, source: VideoSource) = withContext(Dispatchers.IO) {
        val factory = factory(context, source)
        val uri = Uri.parse(source.url)
        val hls = uri.path.orEmpty().endsWith(".m3u8", true) || source.mimeType?.contains("mpegurl", true) == true
        if (hls) {
            fun playlist(target: Uri) = DataSourceInputStream(factory.createDataSource(), DataSpec(target)).use {
                HlsPlaylistParser().parse(target, it)
            }
            val first = playlist(uri)
            val media = when (first) {
                is HlsMediaPlaylist -> first
                is HlsMultivariantPlaylist -> first.variants.minByOrNull { it.format.bitrate.takeIf { b -> b > 0 } ?: Int.MAX_VALUE }
                    ?.let { playlist(it.url) as? HlsMediaPlaylist }
                else -> null
            } ?: return@withContext
            if (!media.hasEndTag) return@withContext // Never cache a moving live window as an adjacent episode.
            val segment = media.segments.firstOrNull() ?: return@withContext
            for (item in listOfNotNull(segment.initializationSegment, segment)) {
                currentCoroutineContext().ensureActive()
                val target = java.net.URI(media.baseUri).resolve(item.url).toString()
                val length = if (item.byteRangeLength > 0) minOf(item.byteRangeLength, 512 * 1024L) else 512 * 1024L
                CacheWriter(factory.createDataSource(), DataSpec.Builder().setUri(target)
                    .setPosition(item.byteRangeOffset).setLength(length).build(), null, null).cache()
            }
        } else if (uri.path.orEmpty().endsWith(".mp4", true) || source.mimeType == "video/mp4") {
            currentCoroutineContext().ensureActive()
            CacheWriter(factory.createDataSource(), DataSpec.Builder().setUri(uri).setLength(512 * 1024L).build(), null, null).cache()
        }
    }
}
