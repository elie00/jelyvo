package ca.ilianokokoro.umihi.music.ui.screens.library

import ca.ilianokokoro.umihi.music.models.Library
import ca.ilianokokoro.umihi.music.models.LocalPlaylist
import ca.ilianokokoro.umihi.music.models.PlaylistInfo
import ca.ilianokokoro.umihi.music.models.Song
import androidx.compose.runtime.Immutable


@Immutable
data class LibraryState(
    val screenState: ScreenState = ScreenState.Loading,
    val isRefreshing: Boolean = false,
    val playlists: List<PlaylistInfo> = emptyList(),
    val localPlaylists: List<LocalPlaylist> = emptyList(),
    val localSongs: List<Song> = emptyList(),
    val localSongsLoaded: Boolean = false,
    /** One-shot message (string resource id) to surface an import result; null when consumed. */
    val importMessage: Int? = null,
)

@Immutable
sealed class ScreenState {
    data class Success(val library: Library) : ScreenState()
    data object LoggedOut : ScreenState()
    data object Loading : ScreenState()
    data class Error(val exception: Exception) : ScreenState()
}
