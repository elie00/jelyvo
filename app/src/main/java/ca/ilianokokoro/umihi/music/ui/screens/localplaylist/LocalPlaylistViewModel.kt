package ca.ilianokokoro.umihi.music.ui.screens.localplaylist

import android.app.Application
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printe
import ca.ilianokokoro.umihi.music.core.managers.PlayerManager
import ca.ilianokokoro.umihi.music.data.repositories.LocalPlaylistRepository
import ca.ilianokokoro.umihi.music.models.LocalPlaylistSong
import ca.ilianokokoro.umihi.music.models.Playlist
import ca.ilianokokoro.umihi.music.models.PlaylistInfo
import ca.ilianokokoro.umihi.music.models.SongSort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LocalPlaylistViewModel(
    private val playlistId: String,
    application: Application,
) : AndroidViewModel(application) {

    private val repository = LocalPlaylistRepository(application)

    private val _uiState = MutableStateFlow(LocalPlaylistState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeById(playlistId).collect { playlist ->
                _uiState.update {
                    if (playlist == null) {
                        it.copy(loading = false, notFound = true)
                    } else {
                        it.copy(
                            loading = false,
                            notFound = false,
                            name = playlist.name,
                            description = playlist.description.orEmpty(),
                            songs = repository.songsOf(playlist),
                        )
                    }
                }
            }
        }
    }

    private fun asPlaylist(songs: List<LocalPlaylistSong>): Playlist =
        Playlist(
            info = PlaylistInfo(id = playlistId, title = _uiState.value.name),
            songs = songs.map { it.toSong() },
        )

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onSortChange(sort: SongSort) {
        _uiState.update { it.copy(sort = sort) }
    }

    fun play(startIndex: Int = 0) {
        // [startIndex] is a position in the *shown* list, so the queue has to be that same list.
        val songs = _uiState.value.visibleSongs
        if (songs.isEmpty()) return
        PlayerManager.playPlaylist(asPlaylist(songs), startIndex)
    }

    fun shuffle() {
        val songs = _uiState.value.visibleSongs
        if (songs.isEmpty()) return
        PlayerManager.shufflePlaylist(asPlaylist(songs))
    }

    /**
     * Persists a new song order (called when a drag gesture finishes). [songs] must be the FULL
     * stored list: the screen only enables the drag handle while [LocalPlaylistState.isManualOrder],
     * so a filtered or sorted view can never reach this and write shown-list positions over the
     * stored order.
     */
    fun persistOrder(songs: List<LocalPlaylistSong>) {
        viewModelScope.launch {
            repository.updateSongs(playlistId, songs)
        }
    }

    fun removeSong(uid: String) {
        viewModelScope.launch {
            val updated = _uiState.value.songs.filterNot { it.uid == uid }
            repository.updateSongs(playlistId, updated)
        }
    }

    fun rename(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.rename(playlistId, trimmed)
        }
    }

    /** Unlike [rename], a blank value is accepted: it clears the description back to NULL. */
    fun updateDescription(description: String) {
        viewModelScope.launch {
            repository.updateDescription(playlistId, description)
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.delete(playlistId)
            onDeleted()
        }
    }

    private fun showToast(@StringRes messageRes: Int) {
        val app = getApplication<Application>()
        Toast.makeText(app, app.getString(messageRes), Toast.LENGTH_SHORT).show()
    }

    fun export(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                repository.exportToUri(context, uri, _uiState.value.name, _uiState.value.songs)
                showToast(R.string.playlist_exported_toast)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (ex: Exception) {
                printe(message = "Failed to export playlist", exception = ex)
                showToast(R.string.playlist_export_failed_toast)
            }
        }
    }

    companion object {
        fun Factory(playlistId: String, application: Application): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    LocalPlaylistViewModel(playlistId, application)
                }
            }
    }
}
