package fr.nekotv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import java.io.File

private const val DOWNLOAD_CHANNEL_ID = "downloads"
private const val DOWNLOAD_NOTIFICATION_ID = 1
private const val DOWNLOAD_SERVICE_SCOPE = "downloads"

@UnstableApi
internal object OfflineDownloads {
    private var downloadManager: DownloadManager? = null

    /** Keeps download UI dependent on the app's validated native source model. */

    private fun getCache(context: Context): SimpleCache =
        SimpleCache(File(context.applicationContext.filesDir, "downloads"),
            LeastRecentlyUsedCacheEvictor(1024L * 1024 * 1024 * 10), // 10 GB limit for now
            StandaloneDatabaseProvider(context.applicationContext))

    fun getDownloadManager(context: Context): DownloadManager =
        downloadManager ?: synchronized(this) {
            downloadManager ?: DownloadManager(
                context.applicationContext,
                StandaloneDatabaseProvider(context.applicationContext),
                getCache(context),
                DefaultHttpDataSource.Factory(),
                java.util.concurrent.Executors.newSingleThreadExecutor()
            ).also { downloadManager = it }
        }

    fun download(context: Context, source: VideoSource, title: String, contentId: String, type: String) {
        // 1. Generate Canonical ID: contentId + quality + language
        val canonicalId = "${contentId}_${source.quality}_${source.language}".hashCode().toString()
        
        // 2. Create/Update Download Job in DB
        val db = com.streamflixreborn.streamflix.database.AppDatabase.getInstance(context)
        val jobDao = db.downloadJobDao()
        
        val existingJob = jobDao.getJobById(canonicalId)
        if (existingJob != null && (existingJob.status == "DOWNLOADING" || existingJob.status == "PENDING")) {
            return // Already in queue
        }

        val job = com.streamflixreborn.streamflix.database.entities.DownloadJob(
            id = canonicalId,
            contentId = contentId,
            type = type,
            title = title,
            quality = source.quality,
            language = source.language,
            subtitles = source.subtitles.joinToString(",") { it.language },
            source = source.provider,
            status = "PENDING",
            progress = 0,
            speed = "0 KB/s",
            error = null,
            attempts = 0,
            localPath = null,
            priority = 0,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        jobDao.insertJob(job)

        // 3. Trigger Media3 Download
        val mimeType = when {
            source.mimeType != null -> source.mimeType
            source.url.endsWith(".m3u8", true) -> "application/vnd.apple.mpegurl"
            source.url.endsWith(".mpd", true) -> "application/dash+xml"
            else -> "video/mp4"
        }
        
        val request = DownloadRequest.Builder(canonicalId, Uri.parse(source.url))
            .setMimeType(mimeType)
            .build()
            
        DownloadService.sendAddDownload(context, OfflineDownloadService::class.java, request, false)
    }

    fun stateFor(context: Context, source: VideoSource): String? = getAllDownloads(context)
        .firstOrNull { it.request.uri.toString() == source.url || it.request.id == source.url.hashCode().toString() }
        ?.let { download ->
            when (download.state) {
                androidx.media3.exoplayer.offline.Download.STATE_COMPLETED -> "Terminé"
                androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING -> "${download.percentDownloaded.toInt().coerceIn(0, 99)} %"
                androidx.media3.exoplayer.offline.Download.STATE_QUEUED -> "En file"
                androidx.media3.exoplayer.offline.Download.STATE_FAILED -> "Échec"
                else -> null
            }
        }

    fun getAllDownloads(context: Context): List<androidx.media3.exoplayer.offline.Download> {
        val downloads = mutableListOf<androidx.media3.exoplayer.offline.Download>()
        val cursor = getDownloadManager(context).downloadIndex.getDownloads()
        try { while (cursor.moveToNext()) downloads.add(cursor.download) } finally { cursor.close() }
        return downloads
    }

    fun setListener(context: Context, listener: DownloadManager.Listener) {
        getDownloadManager(context).addListener(listener)
    }

    fun removeListener(context: Context, listener: DownloadManager.Listener) {
        downloadManager?.removeListener(listener)
    }
}

@UnstableApi
internal class OfflineDownloadService : DownloadService(
    DOWNLOAD_NOTIFICATION_ID,
    1000L,
    DOWNLOAD_CHANNEL_ID,
    R.string.download_notification_title,
    R.string.download_notification_description
) {
    override fun getDownloadManager(): DownloadManager = OfflineDownloads.getDownloadManager(this)

    override fun getScheduler(): androidx.media3.exoplayer.scheduler.Scheduler? = null

    override fun getForegroundNotification(
        downloads: MutableList<androidx.media3.exoplayer.offline.Download>,
        notMetRequirements: Int
    ): Notification {
        return NotificationCompat.Builder(this, DOWNLOAD_CHANNEL_ID)
            .setContentTitle("Téléchargements en cours (${downloads.size})")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()
    }
}
