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
    /** Mosaic cover URLs per local playlist id; decoded once per DB emission, not per row. */
    val localPlaylistCovers: Map<String, List<String>> = emptyMap(),
    val localSongs: List<Song> = emptyList(),
    val localSongsLoaded: Boolean = false,
    /** One-shot message (string resource id) to surface an import result; null when consumed. */
    val importMessage: Int? = null,
    /** Raw text of the liked-songs search field, updated on every keystroke. */
    val likedSongsSearchQuery: String = "",
    /** [likedSongsSearchQuery] after a short debounce; drives the actual filtering. */
    val debouncedLikedSongsSearchQuery: String = "",
)

@Immutable
sealed class ScreenState {
    data class Success(val library: Library) : ScreenState()
    data object LoggedOut : ScreenState()
    data object Loading : ScreenState()
    data class Error(val exception: Exception) : ScreenState()
}
