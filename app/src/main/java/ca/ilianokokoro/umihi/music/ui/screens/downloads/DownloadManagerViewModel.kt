package ca.ilianokokoro.umihi.music.ui.screens.downloads

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.work.WorkInfo
import ca.ilianokokoro.umihi.music.core.StorageCategory
import ca.ilianokokoro.umihi.music.core.StorageInspector
import ca.ilianokokoro.umihi.music.core.workers.DownloadWorkContract
import ca.ilianokokoro.umihi.music.data.database.AppDatabase
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository
import ca.ilianokokoro.umihi.music.data.repositories.DownloadRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class DownloadManagerTask(
    val id: UUID,
    val title: String,
    val kind: String,
    val state: WorkInfo.State,
    val completed: Int,
    val total: Int,
    val failed: Int,
) {
    val progress: Float
        get() = if (total <= 0) 0f else completed.toFloat().div(total).coerceIn(0f, 1f)
    val isActive: Boolean
        get() = state == WorkInfo.State.RUNNING || state == WorkInfo.State.ENQUEUED ||
            state == WorkInfo.State.BLOCKED
    val hasIssue: Boolean get() = state == WorkInfo.State.FAILED || failed > 0
}

data class DownloadManagerState(
    val loading: Boolean = true,
    val active: List<DownloadManagerTask> = emptyList(),
    val issues: List<DownloadManagerTask> = emptyList(),
    val downloadedCount: Int = 0,
    val storageBytes: Long = 0,
)

class DownloadManagerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DownloadRepository(application)
    private val datastore = DatastoreRepository(application)
    private val database = AppDatabase.getInstance(application)
    private val _state = MutableStateFlow(DownloadManagerState())
    val state = _state.asStateFlow()
    private var workById: Map<UUID, WorkInfo> = emptyMap()

    init {
        viewModelScope.launch {
            combine(
                repository.getAllDownloadWorkFlow(),
                database.songRepository().observeDownloadedIds(),
            ) { work, downloadedIds -> work to downloadedIds }
                .collect { (work, downloadedIds) ->
                    workById = work.associateBy(WorkInfo::id)
                    val tasks = withContext(Dispatchers.IO) {
                        buildList {
                            work.forEach { info -> add(toTask(info)) }
                        }
                    }
                    val storage = StorageInspector.measure(getApplication())
                    _state.value = DownloadManagerState(
                        loading = false,
                        active = tasks.filter(DownloadManagerTask::isActive),
                        issues = tasks.filter(DownloadManagerTask::hasIssue).takeLast(MAX_ISSUES),
                        downloadedCount = downloadedIds.size,
                        storageBytes = storage.bytesFor(StorageCategory.Downloads),
                    )
                }
        }
    }

    fun cancel(id: UUID) = repository.cancelWork(id)

    fun cancelAll() = repository.cancelAllWorks()

    fun retry(id: UUID) {
        val work = workById[id] ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val useMetered = datastore.settings.first().downloadOnMetered
            repository.retryWork(work, useMetered)
        }
    }

    private suspend fun toTask(info: WorkInfo): DownloadManagerTask {
        val source = if (info.state.isFinished) info.outputData else info.progress
        val playlistId = DownloadWorkContract.playlistId(info.tags).orEmpty()
        val playlist = playlistId.takeIf(String::isNotBlank)
            ?.let { database.playlistRepository().getPlaylistById(it) }
        val songId = DownloadWorkContract.songId(info.tags)
        val song = songId?.let { database.songRepository().getSong(it) }
        val title = source.getString(DownloadWorkContract.KEY_TITLE)
            ?: song?.title
            ?: playlist?.info?.title
            ?: getApplication<Application>().getString(
                ca.ilianokokoro.umihi.music.R.string.download_manager_title,
            )
        val total = source.getInt(DownloadWorkContract.KEY_TOTAL, 0)
            .takeIf { it > 0 }
            ?: playlist?.songs?.size
            ?: 1
        return DownloadManagerTask(
            id = info.id,
            title = title,
            kind = if (songId == null) {
                DownloadWorkContract.KIND_PLAYLIST
            } else {
                DownloadWorkContract.KIND_SONG
            },
            state = info.state,
            completed = source.getInt(DownloadWorkContract.KEY_COMPLETED, 0),
            total = total,
            failed = source.getInt(DownloadWorkContract.KEY_FAILED, 0),
        )
    }

    companion object {
        private const val MAX_ISSUES = 20

        fun Factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer { DownloadManagerViewModel(application) }
        }
    }
}
