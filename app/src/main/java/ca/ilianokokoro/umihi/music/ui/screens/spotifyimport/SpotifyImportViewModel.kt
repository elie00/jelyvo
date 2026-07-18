package ca.ilianokokoro.umihi.music.ui.screens.spotifyimport

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ca.ilianokokoro.umihi.music.core.spotify.SpotifyApiClient
import ca.ilianokokoro.umihi.music.data.repositories.SpotifyImportRepository
import ca.ilianokokoro.umihi.music.extensions.toException
import ca.ilianokokoro.umihi.music.models.spotify.SpotifyImportTrack
import ca.ilianokokoro.umihi.music.models.spotify.SpotifyPlaylist
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SpotifyImportViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(SpotifyImportState())
    val uiState = _uiState.asStateFlow()

    private val importRepository = SpotifyImportRepository()

    /**
     * Called by the OAuth glue (orchestrator) once the access token has been obtained
     * from the umihi://spotify-callback redirect. Stores it and loads the playlists.
     */
    fun onAuthCompleted(accessToken: String) {
        _uiState.update { it.copy(accessToken = accessToken) }
        loadPlaylists()
    }

    fun loadPlaylists() {
        val token = _uiState.value.accessToken ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(screenState = ScreenState.LoadingPlaylists) }
            try {
                val playlists = SpotifyApiClient.getCurrentUserPlaylists(token)
                _uiState.update {
                    it.copy(screenState = ScreenState.PlaylistsLoaded(playlists))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(screenState = ScreenState.Error(e.toException())) }
            }
        }
    }

    fun importPlaylist(playlist: SpotifyPlaylist) {
        val token = _uiState.value.accessToken ?: return
        viewModelScope.launch {
            try {
                val tracks = SpotifyApiClient.getPlaylistTracks(token, playlist.id)
                runImport(tracks)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(screenState = ScreenState.Error(e.toException())) }
            }
        }
    }

    fun importLikedSongs() {
        val token = _uiState.value.accessToken ?: return
        viewModelScope.launch {
            try {
                val tracks = SpotifyApiClient.getSavedTracks(token)
                runImport(tracks)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(screenState = ScreenState.Error(e.toException())) }
            }
        }
    }

    /** Returns to the playlist list after an import completes or fails. */
    fun backToPlaylists() {
        loadPlaylists()
    }

    private suspend fun runImport(tracks: List<SpotifyImportTrack>) {
        importRepository.importTracks(tracks).collect { progress ->
            _uiState.update {
                it.copy(
                    screenState = if (progress.isComplete) {
                        ScreenState.ImportComplete(progress)
                    } else {
                        ScreenState.Importing(progress)
                    }
                )
            }
        }
    }

    companion object {
        fun Factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SpotifyImportViewModel(application)
            }
        }
    }
}
