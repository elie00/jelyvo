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
        // Flat 2 is the first song: index 0 is the header, index 1 the play/shuffle row.
        assertEquals(listOf("B", "A", "C", "D"), moveByFlatIndex(songs, 2, 3))
    }

    @Test
    fun `dragging the last song does not throw`() {
        // Flat 5 addresses songs[3]; using the flat index raw would call removeAt(5) on 4 elements.
        assertEquals(listOf("A", "B", "D", "C"), moveByFlatIndex(songs, 5, 4))
    }

    @Test
    fun `moving the last song to the top keeps every song`() {
        val moved = moveByFlatIndex(songs, 5, 2)
        assertEquals(listOf("D", "A", "B", "C"), moved)
        assertEquals(songs.size, moved.size)
    }

    @Test
    fun `a drag onto the header rows is a no-op instead of a crash`() {
        assertEquals(songs, moveByFlatIndex(songs, 3, 0))
    }

    @Test
    fun `out-of-range and identity moves leave the list untouched`() {
        assertEquals(songs, moveByFlatIndex(songs, 99, 2))
        assertEquals(songs, moveByFlatIndex(songs, 3, 3))
        assertEquals(emptyList<String>(), moveByFlatIndex(emptyList<String>(), 2, 3))
    }
}
