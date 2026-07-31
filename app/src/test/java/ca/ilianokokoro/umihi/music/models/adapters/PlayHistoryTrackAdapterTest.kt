package ca.ilianokokoro.umihi.music.models.adapters

import ca.ilianokokoro.umihi.music.core.domain.ArtistRef
import ca.ilianokokoro.umihi.music.core.domain.Track
import ca.ilianokokoro.umihi.music.models.PlayHistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayHistoryTrackAdapterTest {
    @Test
    fun `history metadata round trips through Track`() {
        val entry = PlayHistoryEntry(
            id = 7,
            youtubeId = "track-id",
            title = "Title",
            artist = "Artist",
            thumbnailHref = "https://art",
            duration = "3:42",
            isExplicit = true,
            playedAt = 1_000L,
        )

        val restored = entry.toTrack().toPlayHistoryEntry(playedAt = 2_000L)

        assertEquals(entry.youtubeId, restored.youtubeId)
        assertEquals(entry.title, restored.title)
        assertEquals(entry.artist, restored.artist)
        assertEquals(entry.thumbnailHref, restored.thumbnailHref)
        assertEquals(entry.duration, restored.duration)
        assertEquals(entry.isExplicit, restored.isExplicit)
        assertEquals(2_000L, restored.playedAt)
        assertEquals(0L, restored.id)
    }

    @Test
    fun `domain track creates a complete history row`() {
        val entry = Track(
            id = "domain-id",
            title = "Domain title",
            artist = ArtistRef(name = "Domain artist"),
            durationMs = 125_000L,
            artworkUrl = "https://domain-art",
        ).toPlayHistoryEntry(playedAt = 42L)

        assertEquals("domain-id", entry.youtubeId)
        assertEquals("Domain title", entry.title)
        assertEquals("Domain artist", entry.artist)
        assertEquals("2:05", entry.duration)
        assertEquals("https://domain-art", entry.thumbnailHref)
        assertEquals(42L, entry.playedAt)
        assertEquals(0L, entry.positionMs)
        assertEquals(0L, entry.durationMs)
    }

    @Test
    fun `blank artwork and unparsable duration stay absent on the track`() {
        val track = PlayHistoryEntry(
            youtubeId = "sparse",
            duration = "",
            thumbnailHref = "",
            playedAt = 0L,
        ).toTrack()

        assertNull(track.artworkUrl)
        assertNull(track.durationMs)
    }
}
