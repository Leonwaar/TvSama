package fr.nekotv.downloads

import android.content.Context
import android.os.Environment
import java.io.File
import java.util.regex.Pattern

object StorageManager {
    private const val FILMS_DIR = "Films"
    private const val SERIES_DIR = "Séries"
    
    private val INVALID_CHARS = Pattern.compile("[\\\\/:*?\"<>|]")

    fun getRootDirectory(context: Context): File {
        return context.getExternalFilesDir(null) ?: context.filesDir
    }

    fun sanitize(name: String): String {
        var sanitized = name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        if (sanitized.length > 255) {
            sanitized = sanitized.substring(0, 252) + "..."
        }
        return sanitized
    }

    fun getMoviePath(context: Context, title: String, year: Int?, extension: String): File {
        val folderName = sanitize("$title (${year ?: "Unknown"})")
        val fileName = sanitize("$title (${year ?: "Unknown"})$extension")
        val dir = File(getRootDirectory(context), "$FILMS_DIR/$folderName")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, fileName)
    }

    fun getEpisodePath(context: Context, seriesTitle: String, year: Int?, season: Int, episode: Int, episodeTitle: String, extension: String): File {
        val folderName = sanitize("$seriesTitle (${year ?: "Unknown"})")
        val seasonDirName = "Saison ${season.toString().padStart(2, '0')}"
        val fileName = sanitize("$seriesTitle - S${season.toString().padStart(2, '0')}E${episode.toString().padStart(2, '0')} - $episodeTitle$extension")
        
        val dir = File(getRootDirectory(context), "$SERIES_DIR/$folderName/$seasonDirName")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, fileName)
    }

    fun getAvailableSpace(context: Context): Long {
        return getRootDirectory(context).usableSpace
    }

    fun hasEnoughSpace(context: Context, requiredBytes: Long, thresholdBytes: Long = 100 * 1024 * 1024): Boolean {
        return getAvailableSpace(context) > (requiredBytes + thresholdBytes)
    }
}
