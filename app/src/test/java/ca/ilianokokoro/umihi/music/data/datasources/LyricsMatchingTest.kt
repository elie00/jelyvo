package ca.ilianokokoro.umihi.music.data.datasources

import ca.ilianokokoro.umihi.music.models.dto.LrclibResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricsMatchingTest {
    @Test
    fun `matching prefers title artist duration and synced lyrics`() {
        val wrongArtist = candidate("Halo", "Machine Head", 220.0, synced = true)
        val plain = candidate("Halo", "Beyoncé", 261.0, synced = false)
        val synced = candidate("Halo", "Beyonce", 261.5, synced = true)

        val selected = selectBestLyricsCandidate(
            results = listOf(wrongArtist, plain, synced),
            title = "Halo (Official Video)",
            artist = "Beyoncé",
            durationSeconds = 261,
        )

        assertEquals(synced, selected)
    }

    @Test
    fun `unrelated fuzzy result is rejected`() {
        val selected = selectBestLyricsCandidate(
            results = listOf(candidate("Another Song", "Someone Else", 180.0, synced = true)),
            title = "Halo",
            artist = "Beyoncé",
            durationSeconds = 261,
        )

        assertNull(selected)
    }

    private fun candidate(
        title: String,
        artist: String,
        duration: Double,
        synced: Boolean,
    ) = LrclibResponse(
        trackName = title,
        artistName = artist,
        duration = duration,
        plainLyrics = "line",
        syncedLyrics = if (synced) "[00:01.00]line" else null,
    )
}
