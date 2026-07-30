package ca.ilianokokoro.umihi.music.ui.components

import ca.ilianokokoro.umihi.music.models.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SongDragAndDropTest {
    @Test
    fun `local song payload is accepted`() {
        val song = Song(youtubeId = "song-id", title = "A title")

        assertEquals(song, songFromDragLocalState(song))
    }

    @Test
    fun `foreign payload is rejected`() {
        assertNull(songFromDragLocalState("song-id"))
        assertNull(songFromDragLocalState(null))
    }
}
