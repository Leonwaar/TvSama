package fr.nekotv.downloads

import com.streamflixreborn.streamflix.database.dao.DownloadJobDao
import com.streamflixreborn.streamflix.database.entities.DownloadJob
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    private val downloadJobDao: DownloadJobDao
) {
    fun getAllJobs(): List<DownloadJob> = downloadJobDao.getAllJobs()
    
    fun getJobById(id: String): DownloadJob? = downloadJobDao.getJobById(id)
    
    fun insertJob(job: DownloadJob) = downloadJobDao.insertJob(job)
    
    fun updateJob(job: DownloadJob) = downloadJobDao.updateJob(job)
    
    fun deleteJob(id: String) = downloadJobDao.deleteJob(id)
    
    fun countByContentId(contentId: String): Int = downloadJobDao.countByContentId(contentId)
}
