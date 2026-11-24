package fr.nekotv.downloads

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.streamflixreborn.streamflix.database.dao.DownloadJobDao
import com.streamflixreborn.streamflix.database.entities.DownloadJob
import java.io.InputStream
import java.nio.charset.StandardCharsets

class AppDownloadHandler(private val dao: DownloadJobDao) : DownloadHandler {
    private val gson = Gson()

    override fun getAllJobs(): String {
        return gson.toJson(dao.getAllJobs())
    }

    override fun getJob(id: String): String {
        val job = dao.getJobById(id)
        return job?.let { gson.toJson(it) } ?: "{\"error\":\"not_found\"}"
    }

    override fun createJob(body: InputStream): String {
        val json = readBody(body)
        val obj = JsonParser.parseString(json).getAsJsonObject()
        val job = DownloadJob(
            id = obj.get("id").asString,
            contentId = obj.get("contentId").asString,
            type = obj.get("type").asString,
            title = obj.get("title").asString,
            quality = obj.get("quality").asString,
            language = obj.get("language").asString,
            subtitles = obj.get("subtitles").asString,
            source = obj.get("source").asString,
            status = "PENDING",
            progress = 0,
            speed = "0 KB/s",
            error = null,
            attempts = 0,
            localPath = obj.get("localPath").asString,
            priority = obj.get("priority").asInt,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        dao.insertJob(job)
        return gson.toJson(job)
    }

    override fun updateJob(id: String, body: InputStream): String {
        val json = readBody(body)
        val obj = JsonParser.parseString(json).getAsJsonObject()
        val oldJob = dao.getJobById(id) ?: return "{\"error\":\"not_found\"}"
        
        val updatedJob = oldJob.copy(
            status = if (obj.has("status")) obj.get("status").asString else oldJob.status,
            progress = if (obj.has("progress")) obj.get("progress").asInt else oldJob.progress,
            error = if (obj.has("error")) obj.get("error").asString else oldJob.error,
            attempts = if (obj.has("attempts")) obj.get("attempts").asInt else oldJob.attempts,
            updatedAt = System.currentTimeMillis()
        )
        dao.updateJob(updatedJob)
        return gson.toJson(updatedJob)
    }

    override fun deleteJob(id: String): String {
        dao.deleteJob(id)
        return "{\"status\":\"deleted\"}"
    }

    override fun getLibrary(): String {
        val completed = dao.getAllJobs().filter { it.status == "COMPLETED" }
        return gson.toJson(completed)
    }

    private fun readBody(isStream: InputStream): String {
        return try {
            String(isStream.readAllBytes(), StandardCharsets.UTF_8)
        } catch (e: Exception) {
            "{}"
        }
    }
}
