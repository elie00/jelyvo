package ca.ilianokokoro.umihi.music.ui.screens.localplaylist

import androidx.compose.runtime.Immutable
import ca.ilianokokoro.umihi.music.extensions.containsNormalized
import ca.ilianokokoro.umihi.music.models.LocalPlaylistSong
import ca.ilianokokoro.umihi.music.models.SongSort
import ca.ilianokokoro.umihi.music.models.sortedForPlaylist

@Immutable
data class LocalPlaylistState(
    val loading: Boolean = true,
    val name: String = "",
    val description: String = "",
    val songs: List<LocalPlaylistSong> = emptyList(),
    val notFound: Boolean = false,
    val searchQuery: String = "",
    val sort: SongSort = SongSort.PLAYLIST_ORDER,
) {
    /**
     * True while the list draws the stored order untouched — the only state a drag may start from.
     *
     * The reorder callback reports positions in the *shown* list while persistOrder writes the
     * *stored* list, and the two only line up when nothing is hidden and nothing is permuted.
     * Filter three songs out and "move row 2 onto row 5" becomes "move stored index 2 to stored
     * index 5", silently reshuffling rows the user cannot see. This is why CHANTIER.md records the
     * screen as deliberately unfiltered until now.
     */
    val isManualOrder: Boolean
        get() = searchQuery.isBlank() && sort == SongSort.PLAYLIST_ORDER

    /** The songs exactly as the list draws them: search filter first, then sort. */
    val visibleSongs: List<LocalPlaylistSong>
        get() {
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
