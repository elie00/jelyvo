package ca.ilianokokoro.umihi.music.data.repositories

import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.models.importing.MusicImportTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalMusicImportRepositoryTest {

    @Test
    fun `matching prefers title artist and duration over result order`() {
        val wanted = MusicImportTrack(
            title = "Instant Crush",
            artist = "Daft Punk",
            durationMs = 337_000,
        )
        val candidates = listOf(
            Song(
                youtubeId = "cover",
                title = "Instant Crush",
                artist = "Acoustic Cover Band",
                duration = "3:10",
            ),
            Song(
                youtubeId = "official",
                title = "Instant Crush",
                artist = "Daft Punk",
                duration = "5:37",
            ),
        )

        assertEquals("official", chooseBestImportMatch(wanted, candidates)?.youtubeId)
    }

    @Test
    fun `matching is accent insensitive`() {
        val wanted = MusicImportTrack(title = "Tout oublier", artist = "Angèle")
        val candidate = Song(
            youtubeId = "match",
            title = "TOUT OUBLIER",
            artist = "Angele",
            duration = "3:22",
        )

        assertEquals("match", chooseBestImportMatch(wanted, listOf(candidate))?.youtubeId)
    }

    @Test
    fun `unrelated search results are rejected`() {
        val wanted = MusicImportTrack(title = "Midnight City", artist = "M83")
        val unrelated = Song(
            youtubeId = "wrong",
            title = "Blinding Lights",
            artist = "The Weeknd",
            duration = "3:20",
        )

        assertNull(chooseBestImportMatch(wanted, listOf(unrelated)))
    }
}
