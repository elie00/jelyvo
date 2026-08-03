package ca.ilianokokoro.umihi.music.ui.screens.artist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.data.repositories.ArtistRepository
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository
import ca.ilianokokoro.umihi.music.models.ArtistAlbum
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ArtistReleasesState {
    data object Loading : ArtistReleasesState
    data class Success(val releases: List<ArtistAlbum>) : ArtistReleasesState
    data class Error(val exception: Exception) : ArtistReleasesState
}

class ArtistReleasesViewModel(
    application: Application,
    private val browseId: String,
    private val params: String,
) : AndroidViewModel(application) {
    private val repository = ArtistRepository()
    private val datastoreRepository = DatastoreRepository(application)
    private val _uiState = MutableStateFlow<ArtistReleasesState>(ArtistReleasesState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val settings = datastoreRepository.getSettings()
            repository.getArtistReleases(browseId, params, settings).collect { result ->
                _uiState.value = when (result) {
                    is ApiResult.Loading -> ArtistReleasesState.Loading
                    is ApiResult.Success -> ArtistReleasesState.Success(result.data)
                    is ApiResult.Error -> ArtistReleasesState.Error(result.exception)
                }
            }
        }
    }

    companion object {
        fun Factory(
            application: Application,
            browseId: String,
            params: String,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ArtistReleasesViewModel(application, browseId, params)
            }
        }
    }
}
