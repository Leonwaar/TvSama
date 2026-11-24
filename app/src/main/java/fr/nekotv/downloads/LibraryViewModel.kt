package fr.nekotv.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamflixreborn.streamflix.database.entities.DownloadJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class LibraryViewModel @Inject constructor(
    private val repository: DownloadRepository
) : ViewModel() {

    private val _libraryJobs = MutableStateFlow<List<DownloadJob>>(emptyList())
    val libraryJobs: StateFlow<List<DownloadJob>> = _libraryJobs

    init {
        loadLibrary()
    }

    fun loadLibrary() {
        viewModelScope.launch(Dispatchers.IO) {
            _libraryJobs.value = repository.getAllJobs().filter { it.status == "COMPLETED" }
        }
    }
    
    fun deleteJob(job: DownloadJob) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteJob(job.id)
            loadLibrary()
        }
    }
}
