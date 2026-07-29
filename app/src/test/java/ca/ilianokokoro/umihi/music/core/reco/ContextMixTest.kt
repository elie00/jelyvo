package ca.ilianokokoro.umihi.music.core.reco

import ca.ilianokokoro.umihi.music.models.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContextMixTest {

    private val now = 1_700_000_000_000L

    private fun candidate(id: String, artist: String) =
        DailyMixCandidate(song = Song(youtubeId = id, title = "Title $id", artist = artist))

    private fun profile(hourlyTopArtists: Map<DayBucket, List<String>>) = TasteProfile(
        computedAtMs = now,
        hourlyTopArtists = hourlyTopArtists,
    )

    // --- slot -------------------------------------------------------------------------------

    @Test
    fun `the morning slot runs from 5 to 11`() {
        assertNull(contextMixBucket(4))
        assertEquals(DayBucket.MORNING, contextMixBucket(5))
        assertEquals(DayBucket.MORNING, contextMixBucket(11))
        assertNull(contextMixBucket(12))
    }

    @Test
    fun `the evening slot runs from 18 to 23`() {
        assertNull(contextMixBucket(17))
        assertEquals(DayBucket.EVENING, contextMixBucket(18))
        assertEquals(DayBucket.EVENING, contextMixBucket(23))
        assertNull(contextMixBucket(0))
    }

    // --- candidate bias ---------------------------------------------------------------------

    @Test
    fun `bucket artists move to the front while both halves keep their order`() {
        val candidates = listOf(
            candidate("a", artist = "Other One"),
            candidate("b", artist = "Evening Star"),
            candidate("c", artist = "Other Two"),
            // Same artist through normalization (case), so it must move up too.
            candidate("d", artist = "evening star"),
        )

        val biased = biasCandidatesToBucket(
            candidates = candidates,
            profile = profile(mapOf(DayBucket.EVENING to listOf("Evening Star"))),
            bucket = DayBucket.EVENING,
        )

        assertEquals(listOf("b", "d", "a", "c"), biased.map { it.song.youtubeId })
    }

    @Test
    fun `a bucket with no profile data leaves the pool untouched`() {
        val candidates = listOf(
            candidate("a", artist = "One"),
            candidate("b", artist = "Two"),
        )

        assertEquals(
            candidates,
            biasCandidatesToBucket(candidates, profile(emptyMap()), DayBucket.MORNING),
        )
    }
}
