package ca.ilianokokoro.umihi.music.ui.screens.artist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ca.ilianokokoro.umihi.music.core.domain.CatalogResult
import ca.ilianokokoro.umihi.music.core.domain.MusicCatalog
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printe
import ca.ilianokokoro.umihi.music.data.catalog.YoutubeMusicCatalog
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository
import ca.ilianokokoro.umihi.music.models.adapters.toArtistPage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ArtistViewModel(
    application: Application,
    private val artistId: String,
    private val artistName: String,
) : AndroidViewModel(application) {
    private val datastoreRepository = DatastoreRepository(application)
    private val catalog: MusicCatalog = YoutubeMusicCatalog(
        settingsProvider = { datastoreRepository.getSettings() },
    )

    private val _uiState = MutableStateFlow<ArtistState>(ArtistState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            catalog.artist(artistId, artistName).collect { result ->
                _uiState.update {
                    when (result) {
                        is CatalogResult.Loading -> ArtistState.Loading
                        is CatalogResult.Success -> ArtistState.Success(result.data.toArtistPage())
                        is CatalogResult.Error -> {
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
