package ca.ilianokokoro.umihi.music.ui.screens.album

import android.app.Application
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.domain.CatalogResult
import ca.ilianokokoro.umihi.music.core.domain.MusicCatalog
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printe
import ca.ilianokokoro.umihi.music.core.youtube.YoutubeApiClient
import ca.ilianokokoro.umihi.music.data.catalog.YoutubeMusicCatalog
import ca.ilianokokoro.umihi.music.data.database.AppDatabase
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository
import ca.ilianokokoro.umihi.music.data.repositories.DownloadRepository
import ca.ilianokokoro.umihi.music.data.repositories.LocalPlaylistRepository
import ca.ilianokokoro.umihi.music.models.LocalPlaylist
import ca.ilianokokoro.umihi.music.models.Playlist
import ca.ilianokokoro.umihi.music.models.PlaylistInfo
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.models.adapters.toAlbumPage
import ca.ilianokokoro.umihi.music.models.adapters.toTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AlbumViewModel(
    application: Application,
    private val albumId: String,
    private val albumName: String,
) : AndroidViewModel(application) {
    private val datastoreRepository = DatastoreRepository(application)
    private val catalog: MusicCatalog = YoutubeMusicCatalog(
        settingsProvider = { datastoreRepository.getSettings() },
    )
    private val downloadRepository = DownloadRepository(application)
    private val localPlaylistRepository = LocalPlaylistRepository(application)

    private val _uiState = MutableStateFlow<AlbumState>(AlbumState.Loading)
    val uiState = _uiState.asStateFlow()

    /** Gates the header's logged-in-only actions, same signal as SongListItem rows. */
    val isLoggedIn = datastoreRepository.settings
        .map { !it.cookies.isEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * Etat "album dans la bibliotheque" du header. Seede par la reponse browse quand elle expose
     * le bouton bibliotheque, sinon false ; la bascule est optimiste et annulee en cas d'echec.
     */
    private val _isAlbumSaved = MutableStateFlow(false)
    val isAlbumSaved = _isAlbumSaved.asStateFlow()

    /** Local playlists offered by the "add to playlist" picker of the header menu. */
    val localPlaylists = localPlaylistRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Ids des pistes deja telechargees : un seul Set en memoire partage par toutes les lignes,
    // aucune requete disque par ligne.
    val downloadedIds = AppDatabase.getInstance(application).songRepository()
        .observeDownloadedIds()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            catalog.album(albumId, albumName).collect { result ->
                _uiState.update {
                    when (result) {
                        is CatalogResult.Loading -> AlbumState.Loading
                        is CatalogResult.Success -> {
                            val page = result.data.toAlbumPage()
                            _isAlbumSaved.value = page.isSavedToLibrary ?: false
                            AlbumState.Success(page)
                        }

                        is CatalogResult.Error -> {
                            printe(message = "Failed to load album", exception = result.exception)
                            AlbumState.Error(result.exception)
                        }
                    }
                }
            }
        }
    }

    /**
     * Saves/removes the album from the YT Music library through its audio playlist id.
     * The heart flips immediately (optimistic) and reverts with a toast when the call fails.
     */
    fun toggleAlbumSaved() {
        val page = (uiState.value as? AlbumState.Success)?.page ?: return
        val playlistId = page.playlistId ?: return
        val target = !_isAlbumSaved.value
        _isAlbumSaved.value = target

        viewModelScope.launch {
            try {
                val settings = datastoreRepository.getSettings()
                YoutubeApiClient.setAlbumSaved(playlistId, saved = target, settings)
                showToast(
                    if (target) R.string.album_saved_toast else R.string.album_removed_toast
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                printe(message = "Failed to update album library state", exception = e)
                _isAlbumSaved.value = !target
                showToast(R.string.album_save_failed_toast)
            }
        }
    }

    /** Appends the whole album to an existing local playlist, skipping tracks already in it. */
    fun addAlbumToLocalPlaylist(playlist: LocalPlaylist, songs: List<Song>) {
        if (songs.isEmpty()) return
        viewModelScope.launch {
            localPlaylistRepository.addTracks(
                playlist.id,
                songs.map { it.toTrack() },
            )
            showToast(R.string.album_added_to_playlist_toast, playlist.name)
        }
    }

    /** Creates a new local playlist holding the whole album, named after it. */
    fun createLocalPlaylistFromAlbum(name: String, songs: List<Song>) {
        if (songs.isEmpty()) return
        viewModelScope.launch {
            localPlaylistRepository.createFromTracks(name, songs.map { it.toTrack() })
            showToast(R.string.album_added_to_playlist_toast, name)
        }
    }

    private fun showToast(@StringRes messageRes: Int, vararg formatArgs: Any) {
        val app = getApplication<Application>()
        Toast.makeText(
            app,
            app.getString(messageRes, *formatArgs),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun albumAsPlaylist(songs: List<Song>, cover: String) = Playlist(
        info = PlaylistInfo(id = "album:$albumId", title = albumName, coverHref = cover),
        songs = songs,
    )

    fun downloadAlbum(songs: List<Song>, cover: String) {
        if (songs.isEmpty()) return
        viewModelScope.launch {
            val settings = datastoreRepository.getSettings()
            downloadRepository.downloadPlaylist(
                playlist = albumAsPlaylist(songs, cover),
                useMetered = settings.downloadOnMetered,
            )
            showToast(R.string.download_started_toast)
        }
    }

    fun downloadSong(song: Song, songs: List<Song>, cover: String) {
        if (song.downloaded) return
        viewModelScope.launch {
            val settings = datastoreRepository.getSettings()
            downloadRepository.downloadSong(
                albumAsPlaylist(songs, cover),
                song,
                settings.downloadOnMetered,
            )
            showToast(R.string.download_started_toast)
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
