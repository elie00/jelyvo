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
        // Flat 3 is the first song: 0 header, 1 filter/sort, 2 play/shuffle.
        assertEquals(listOf("B", "A", "C", "D"), moveByFlatIndex(songs, 3, 4))
    }

    @Test
    fun `dragging the last song does not throw`() {
        // Flat 6 addresses songs[3]; using the flat index raw would call removeAt(6) on 4 elements.
        assertEquals(listOf("A", "B", "D", "C"), moveByFlatIndex(songs, 6, 5))
    }

    @Test
    fun `moving the last song to the top keeps every song`() {
        val moved = moveByFlatIndex(songs, 6, 3)
        assertEquals(listOf("D", "A", "B", "C"), moved)
        assertEquals(songs.size, moved.size)
    }

    @Test
    fun `a drag onto the header rows is a no-op instead of a crash`() {
        assertEquals(songs, moveByFlatIndex(songs, 4, 0))
    }

    @Test
    fun `out-of-range and identity moves leave the list untouched`() {
        assertEquals(songs, moveByFlatIndex(songs, 99, 3))
        assertEquals(songs, moveByFlatIndex(songs, 4, 4))
        assertEquals(emptyList<String>(), moveByFlatIndex(emptyList<String>(), 3, 4))
    }
}
