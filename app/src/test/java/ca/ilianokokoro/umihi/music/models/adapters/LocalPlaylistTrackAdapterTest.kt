package ca.ilianokokoro.umihi.music.models.adapters

import ca.ilianokokoro.umihi.music.core.domain.ArtistRef
import ca.ilianokokoro.umihi.music.core.domain.Track
import ca.ilianokokoro.umihi.music.models.LocalPlaylistSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LocalPlaylistTrackAdapterTest {
    @Test
    fun `playlist metadata round trips through Track`() {
        val row = LocalPlaylistSong(
            id = "track-id",
            title = "Title",
            artist = "Artist",
            thumbnailHref = "https://art",
            duration = "3:42",
            uid = "existing-row",
        )

        val restored = row.toTrack().toLocalPlaylistSong()

        assertEquals(row.id, restored.id)
        assertEquals(row.title, restored.title)
        assertEquals(row.artist, restored.artist)
        assertEquals(row.thumbnailHref, restored.thumbnailHref)
        assertEquals(row.duration, restored.duration)
        assertNotEquals(row.uid, restored.uid)
    }

    @Test
    fun `domain track creates a complete local playlist row`() {
        val row = Track(
            id = "domain-id",
            title = "Domain title",
            artist = ArtistRef(name = "Domain artist"),
            durationMs = 125_000L,
            artworkUrl = "https://domain-art",
        ).toLocalPlaylistSong()

        assertEquals("domain-id", row.id)
        assertEquals("Domain title", row.title)
        assertEquals("Domain artist", row.artist)
        assertEquals("2:05", row.duration)
        assertEquals("https://domain-art", row.thumbnailHref)
    }
}
