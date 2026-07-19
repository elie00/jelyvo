package ca.ilianokokoro.umihi.music.ui.screens.artist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printe
import ca.ilianokokoro.umihi.music.data.repositories.ArtistRepository
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ArtistViewModel(
    application: Application,
    private val artistId: String,
    private val artistName: String,
) : AndroidViewModel(application) {
    private val artistRepository = ArtistRepository()
    private val datastoreRepository = DatastoreRepository(application)

    private val _uiState = MutableStateFlow<ArtistState>(ArtistState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val settings = datastoreRepository.getSettings()
            artistRepository.getArtist(artistId, artistName, settings).collect { result ->
                _uiState.update {
                    when (result) {
                        is ApiResult.Loading -> ArtistState.Loading
                        is ApiResult.Success -> ArtistState.Success(result.data)
                        is ApiResult.Error -> {
                            printe(message = "Failed to load artist", exception = result.exception)
                            ArtistState.Error(result.exception)
                        }
                    }
                }
            }
        }
    }

    companion object {
        fun Factory(
            application: Application,
            artistId: String,
            artistName: String,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ArtistViewModel(application, artistId, artistName)
            }
        }
    }
}
