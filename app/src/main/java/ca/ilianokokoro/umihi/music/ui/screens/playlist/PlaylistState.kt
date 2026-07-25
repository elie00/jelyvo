package ca.ilianokokoro.umihi.music.ui.screens.playlist

import ca.ilianokokoro.umihi.music.models.Playlist
import ca.ilianokokoro.umihi.music.models.PlaylistInfo
import androidx.compose.runtime.Immutable
import ca.ilianokokoro.umihi.music.extensions.containsNormalized
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.models.SongSort
import ca.ilianokokoro.umihi.music.models.sortedForPlaylist


@Immutable
data class PlaylistState(
    val screenState: ScreenState,
    val isRefreshing: Boolean = false,
    val isDownloading: Boolean = false,
    val searchQuery: String = "",
    val showingSearch: Boolean = false,
    val sort: SongSort = SongSort.PLAYLIST_ORDER
) {
    /**
     * The songs exactly as the list draws them: search filter first, then sort. One definition, so
     * play, shuffle and a row tap all queue what the user sees — the rule the liked-songs tab
     * already follows. Recomputed on every read, hence the remember() at the call site.
     */
    val visibleSongs: List<Song>
        get() {
            val songs = (screenState as? ScreenState.Success)?.playlist?.songs ?: emptyList()
            val filtered = if (searchQuery.isBlank()) {
                songs
            } else {
                songs.filter { song ->
                    song.title.containsNormalized(searchQuery) ||
                        song.artist.containsNormalized(searchQuery)
                }
            }
            return filtered.sortedForPlaylist(sort)
        }
}

@Immutable
sealed class ScreenState {
    data class Success(
        val playlist: Playlist
    ) : ScreenState()

    data class Loading(
        val playlistInfo: PlaylistInfo
    ) : ScreenState()

    data class Error(val exception: Exception) : ScreenState()
}
