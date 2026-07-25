package ca.ilianokokoro.umihi.music.ui.screens.localplaylist

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The reorder callback reports flat LazyColumn indices while the song list is indexed from 0, so
 * the play/shuffle header offsets every song by one. Using the flat index directly moved the wrong
 * song, and threw IndexOutOfBoundsException on the last row — dragging the bottom song of a local
 * playlist crashed the app.
 */
class ReorderMathTest {

    private val songs = listOf("A", "B", "C", "D")

    @Test
    fun `dragging the first song moves the first song`() {
        // Flat 1 is the first song, not the second.
        assertEquals(listOf("B", "A", "C", "D"), moveByFlatIndex(songs, 1, 2))
    }

    @Test
    fun `dragging the last song does not throw`() {
        // Flat 4 addresses songs[3]; the old code called removeAt(4) on a 4-element list.
        assertEquals(listOf("A", "B", "D", "C"), moveByFlatIndex(songs, 4, 3))
    }

    @Test
    fun `moving the last song to the top keeps every song`() {
        val moved = moveByFlatIndex(songs, 4, 1)
        assertEquals(listOf("D", "A", "B", "C"), moved)
        assertEquals(songs.size, moved.size)
    }

    @Test
    fun `a drag onto the header is a no-op instead of a crash`() {
        assertEquals(songs, moveByFlatIndex(songs, 2, 0))
    }

    @Test
    fun `out-of-range and identity moves leave the list untouched`() {
        assertEquals(songs, moveByFlatIndex(songs, 99, 1))
        assertEquals(songs, moveByFlatIndex(songs, 2, 2))
        assertEquals(emptyList<String>(), moveByFlatIndex(emptyList<String>(), 1, 2))
    }
}
