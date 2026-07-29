package ca.ilianokokoro.umihi.music.ui.screens.musicimport

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.data.repositories.ExternalMusicImportRepository
import ca.ilianokokoro.umihi.music.data.repositories.ExternalPlaylistRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MusicImportViewModel(application: Application) : AndroidViewModel(application) {
    private val playlistRepository = ExternalPlaylistRepository(
        defaultPlaylistName = application.getString(R.string.music_import_default_name)
    )
    private val importRepository = ExternalMusicImportRepository(application)

    private val _uiState = MutableStateFlow(MusicImportState())
    val uiState = _uiState.asStateFlow()

    fun updateLink(link: String) {
        _uiState.update {
            it.copy(
                link = link,
                screenState = if (it.screenState is MusicImportScreenState.Error) {
                    MusicImportScreenState.Ready
                } else {
                    it.screenState
                },
            )
        }
    }

    fun analyze() {
        val link = _uiState.value.link.trim()
        if (link.isBlank()) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(link = link, playlist = null, screenState = MusicImportScreenState.Resolving)
            }
            try {
                val playlist = playlistRepository.resolve(link)
                _uiState.update {
                    it.copy(
                        playlist = playlist,
                        screenState = MusicImportScreenState.Preview,
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(screenState = MusicImportScreenState.Error(exception))
                }
            }
        }
    }

    fun startImport() {
        val playlist = _uiState.value.playlist ?: return
        viewModelScope.launch {
            try {
                importRepository.importPlaylist(playlist).collect { progress ->
                    _uiState.update {
                        it.copy(
                            screenState = if (progress.isComplete) {
                                MusicImportScreenState.Complete(progress)
                            } else {
                                MusicImportScreenState.Importing(progress)
                            },
                        )
                    }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(screenState = MusicImportScreenState.Error(exception))
                }
            }
        }
    }

    fun editLink() {
        _uiState.update { it.copy(playlist = null, screenState = MusicImportScreenState.Ready) }
    }

    companion object {
        fun Factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer { MusicImportViewModel(application) }
        }
    }
}
