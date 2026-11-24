package fr.nekotv.downloads

import android.content.Context
import android.util.Log
import java.io.File

object DiskManager {
    private const val TAG = "DiskManager"

    fun getAvailableSpace(context: Context): Long {
        return StorageManager.getAvailableSpace(context)
    }

    fun getTotalQuota(context: Context): Long = DownloadConfig.TOTAL_QUOTA_BYTES

    fun getAlertThreshold(context: Context): Long = DownloadConfig.ALERT_THRESHOLD_BYTES

    fun hasEnoughSpace(context: Context, requiredBytes: Long): Boolean {
        val free = getAvailableSpace(context)
        val required = requiredBytes + DownloadConfig.MIN_FREE_SPACE_BYTES
        return free > required && free > DownloadConfig.ALERT_THRESHOLD_BYTES
    }

    fun checkQuotaBeforeDownload(context: Context, requiredBytes: Long): QuotaCheckResult {
        val free = getAvailableSpace(context)
        val quota = DownloadConfig.TOTAL_QUOTA_BYTES
        val alertThreshold = DownloadConfig.ALERT_THRESHOLD_BYTES

        if (free <= DownloadConfig.MIN_FREE_SPACE_BYTES) {
            return QuotaCheckResult.Rejected("Espace disque insuffisant. Veuillez libérer de l'espace.")
        }

        if (free <= alertThreshold) {
            return QuotaCheckResult.Warning("Espace disque faible. Le téléchargement peut échouer si l'espace s'épuise.")
        }

        if (free < requiredBytes + DownloadConfig.MIN_FREE_SPACE_BYTES) {
            return QuotaCheckResult.Rejected("Espace disque insuffisant pour ce téléchargement.")
        }

        return QuotaCheckResult.OK
    }

    fun cleanupOrphanedTempFiles(context: Context) {
        val root = StorageManager.getRootDirectory(context)
        val tempDir = File(root, DownloadConfig.TEMP_DIR)
        if (!tempDir.exists()) return

        tempDir.listFiles()?.forEach { file ->
            try {
                file.delete()
                Log.d(TAG, "Deleted orphaned temp file: ${file.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete temp file: ${file.absolutePath}", e)
            }
        }
    }

    /*
    fun cleanupFailedDownloads(context: Context, jobs: List<DownloadJob>) {
        // Delete local files associated with failed/cancelled jobs
        jobs.filter { it.status == "FAILED" || it.status == "CANCELLED" }
            .forEach { job ->
                job.localPath?.let { path ->
                    File(path).delete()
                    Log.d(TAG, "Deleted failed download file: $path")
                }
            }
    }
    */

    fun getDownloadDirectorySize(context: Context): Long {
        var size = 0L
        val root = StorageManager.getRootDirectory(context)
        fun dirSize(dir: File): Long {
            if (!dir.exists()) return 0L
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) size += dirSize(file)
                else size += file.length()
            }
            return size
        }
        dirSize(root)
        return size
    }

    sealed class QuotaCheckResult {
        object OK : QuotaCheckResult()
        data class Warning(val message: String) : QuotaCheckResult()
        data class Rejected(val message: String) : QuotaCheckResult()
    }
}
