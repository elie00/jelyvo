package ca.ilianokokoro.umihi.music.widget

import ca.ilianokokoro.umihi.music.core.domain.ArtistRef
import ca.ilianokokoro.umihi.music.core.domain.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackWidgetStateTest {
    @Test
    fun `widget state maps platform neutral track metadata`() {
        val state = playbackWidgetState(
            track = Track(
                id = "track-id",
                title = "Titre",
                artist = ArtistRef(id = "artist-id", name = "Artiste"),
            ),
            isPlaying = true,
        )

        assertEquals("track-id", state.mediaId)
        assertEquals("Titre", state.title)
        assertEquals("Artiste", state.artist)
        assertTrue(state.isPlaying)
    }

    @Test
    fun `empty widget state is safe when nothing is playing`() {
        val state = playbackWidgetState(track = null, isPlaying = false)

        assertEquals("", state.mediaId)
        assertEquals("", state.title)
        assertEquals("", state.artist)
        assertFalse(state.isPlaying)
    }
}
