package ca.ilianokokoro.umihi.music.ui.screens.playlist

import ca.ilianokokoro.umihi.music.models.Playlist
import ca.ilianokokoro.umihi.music.models.PlaylistInfo
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.models.SongSort
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Play, shuffle and row taps all queue [PlaylistState.visibleSongs]. If the filter and the sort
 * lived anywhere else, the queue would quietly include the rows the user filtered out — which is
 * what this screen did before: it filtered in the list body but played playlist.songs.
 */
class PlaylistStateTest {

    private val songs = listOf(
        Song(youtubeId = "1", title = "Mélodie", artist = "Zaz", duration = "4:00"),
        Song(youtubeId = "2", title = "Ballad", artist = "Alpha", duration = "2:00"),
        Song(youtubeId = "3", title = "Melody Two", artist = "Beta", duration = "3:00"),
    )

    private fun state(query: String = "", sort: SongSort = SongSort.PLAYLIST_ORDER) =
        PlaylistState(
            screenState = ScreenState.Success(Playlist(PlaylistInfo(id = "p"), songs)),
            searchQuery = query,
            sort = sort,
        )

    @Test
    fun `no query and no sort shows the playlist as it came`() {
        assertEquals(songs.map { it.youtubeId }, state().visibleSongs.map { it.youtubeId })
    }

    @Test
    fun `the filter is accent insensitive and covers the artist`() {
        assertEquals(listOf("1", "3"), state(query = "melod").visibleSongs.map { it.youtubeId })
        assertEquals(listOf("2"), state(query = "alpha").visibleSongs.map { it.youtubeId })
    }

    @Test
    fun `the sort applies to what survived the filter, not to the whole playlist`() {
        val visible = state(query = "melod", sort = SongSort.DURATION).visibleSongs
        assertEquals(listOf("3", "1"), visible.map { it.youtubeId })
    }

    @Test
    fun `a non-success screen state has nothing visible`() {
        val loading = PlaylistState(screenState = ScreenState.Loading(PlaylistInfo(id = "p")))
        assertEquals(emptyList<Song>(), loading.visibleSongs)
    }
}
