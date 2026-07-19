package ca.ilianokokoro.umihi.music.ui.screens.album

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printe
import ca.ilianokokoro.umihi.music.data.repositories.AlbumRepository
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AlbumViewModel(
    application: Application,
    private val albumId: String,
    private val albumName: String,
) : AndroidViewModel(application) {
    private val albumRepository = AlbumRepository()
    private val datastoreRepository = DatastoreRepository(application)

    private val _uiState = MutableStateFlow<AlbumState>(AlbumState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val settings = datastoreRepository.getSettings()
            albumRepository.getAlbum(albumId, albumName, settings).collect { result ->
                _uiState.update {
                    when (result) {
                        is ApiResult.Loading -> AlbumState.Loading
                        is ApiResult.Success -> AlbumState.Success(result.data)
                        is ApiResult.Error -> {
                            printe(message = "Failed to load album", exception = result.exception)
                            AlbumState.Error(result.exception)
                        }
                    }
                }
            }
        }
    }

    companion object {
        fun Factory(
            application: Application,
            albumId: String,
            albumName: String,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AlbumViewModel(application, albumId, albumName)
            }
        }
    }
}
