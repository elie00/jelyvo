package ca.ilianokokoro.umihi.music.core.reco

import ca.ilianokokoro.umihi.music.core.playback.PlayEndReason
import ca.ilianokokoro.umihi.music.models.PlayEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HourlyProfileTest {

    private fun event(
        youtubeId: String,
        hourOfDay: Int,
        endReason: PlayEndReason = PlayEndReason.COMPLETED,
    ) = PlayEvent(
        youtubeId = youtubeId,
        playedAt = 1_700_000_000_000L,
        durationListenedMs = 100_000,
        trackDurationMs = 100_000,
        completionRatio = 1f,
        endReason = endReason.name,
        hourOfDay = hourOfDay,
        dayOfWeek = 1,
        audioOutput = null,
        sourceContext = null,
    )

    // --- dayBucket ---

    @Test
    fun `hours map to their bucket`() {
        assertEquals(DayBucket.MORNING, dayBucket(6))
        assertEquals(DayBucket.MORNING, dayBucket(11))
        assertEquals(DayBucket.DAY, dayBucket(12))
        assertEquals(DayBucket.DAY, dayBucket(17))
        assertEquals(DayBucket.EVENING, dayBucket(18))
        assertEquals(DayBucket.EVENING, dayBucket(22))
        assertEquals(DayBucket.NIGHT, dayBucket(23))
        assertEquals(DayBucket.NIGHT, dayBucket(0))
        assertEquals(DayBucket.NIGHT, dayBucket(5))
    }

    // --- hourlyArtistDistribution ---

    @Test
    fun `plays are counted in their bucket, most played artist first`() {
        val distribution = hourlyArtistDistribution(
            events = listOf(
                event("a1", hourOfDay = 8),
                event("a2", hourOfDay = 9),
                event("b1", hourOfDay = 10),
            ),
            artistByYoutubeId = mapOf("a1" to "Alpha", "a2" to "Alpha", "b1" to "Beta"),
        )
        assertEquals(listOf("Alpha", "Beta"), distribution.getValue(DayBucket.MORNING))
        assertFalse(DayBucket.NIGHT in distribution)
    }

    @Test
    fun `early skips do not count towards the hourly profile`() {
        val distribution = hourlyArtistDistribution(
            events = listOf(event("a1", hourOfDay = 20, endReason = PlayEndReason.SKIPPED_EARLY)),
            artistByYoutubeId = mapOf("a1" to "Alpha"),
        )
        assertTrue(distribution.isEmpty())
    }

    @Test
    fun `tracks without a known artist are skipped`() {
        val distribution = hourlyArtistDistribution(
            events = listOf(event("unknown", hourOfDay = 14)),
            artistByYoutubeId = emptyMap(),
        )
        assertTrue(distribution.isEmpty())
    }

    @Test
    fun `artist spellings fold together but the display name is kept`() {
        val distribution = hourlyArtistDistribution(
            events = listOf(event("a1", hourOfDay = 19), event("a2", hourOfDay = 20)),
            artistByYoutubeId = mapOf("a1" to "Mylène Farmer", "a2" to "mylene farmer"),
        )
        assertEquals(listOf("Mylène Farmer"), distribution.getValue(DayBucket.EVENING))
    }

    @Test
    fun `each bucket keeps at most topPerBucket artists`() {
        val events = (1..4).map { index -> event("s$index", hourOfDay = 13) }
        val artists = (1..4).associate { index -> "s$index" to "Artist $index" }
        val distribution = hourlyArtistDistribution(events, artists, topPerBucket = 2)
        assertEquals(2, distribution.getValue(DayBucket.DAY).size)
    }
}
