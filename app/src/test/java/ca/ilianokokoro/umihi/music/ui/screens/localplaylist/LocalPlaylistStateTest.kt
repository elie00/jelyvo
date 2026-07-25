package ca.ilianokokoro.umihi.music.ui.screens.localplaylist

import ca.ilianokokoro.umihi.music.models.LocalPlaylistSong
import ca.ilianokokoro.umihi.music.models.SongSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A drag writes positions from the *shown* list into the *stored* list, so it is only safe while
 * [LocalPlaylistState.isManualOrder]. These pin that flag and the invariant it rests on: in manual
 * order the shown list IS the stored list, element for element. CHANTIER.md records that this
 * screen was left unfiltered precisely because nothing guarded that.
 */
class LocalPlaylistStateTest {

    private val songs = listOf(
        LocalPlaylistSong(id = "1", title = "Mélodie", artist = "Zaz", duration = "4:00"),
        LocalPlaylistSong(id = "2", title = "Ballad", artist = "Alpha", duration = "2:00"),
        LocalPlaylistSong(id = "3", title = "Melody Two", artist = "Beta", duration = "3:00"),
    )

    @Test
    fun `manual order only holds without a query and without a sort`() {
        assertTrue(LocalPlaylistState(songs = songs).isManualOrder)
        assertFalse(LocalPlaylistState(songs = songs, searchQuery = "mel").isManualOrder)
        assertFalse(LocalPlaylistState(songs = songs, sort = SongSort.TITLE).isManualOrder)
    }

    @Test
    fun `manual order shows the stored list unchanged`() {
        assertEquals(songs, LocalPlaylistState(songs = songs).visibleSongs)
    }

    @Test
    fun `a filter hides rows, which is exactly why dragging must stop`() {
        val state = LocalPlaylistState(songs = songs, searchQuery = "melod")
        assertEquals(listOf("1", "3"), state.visibleSongs.map { it.id })
        assertFalse(state.isManualOrder)
    }

    @Test
    fun `a sort permutes rows, which is also why dragging must stop`() {
        val state = LocalPlaylistState(songs = songs, sort = SongSort.DURATION)
        assertEquals(listOf("2", "3", "1"), state.visibleSongs.map { it.id })
        assertFalse(state.isManualOrder)
    }

    @Test
    fun `the filter matches the artist too, accent-insensitively`() {
        val state = LocalPlaylistState(songs = songs, searchQuery = "alpha")
        assertEquals(listOf("2"), state.visibleSongs.map { it.id })
    }
}
