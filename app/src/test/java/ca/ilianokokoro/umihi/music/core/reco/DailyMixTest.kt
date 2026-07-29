package ca.ilianokokoro.umihi.music.core.reco

import ca.ilianokokoro.umihi.music.models.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class DailyMixTest {

    private val now = 1_700_000_000_000L
    private val fresh = now - DAILY_MIX_FATIGUE_MS - 1_000L
    private val fatigued = now - 1_000L

    private fun song(id: String, artist: String = "Artist $id") = Song(
        youtubeId = id,
        title = "Title $id",
        artist = artist,
    )

    private fun candidate(
        id: String,
        artist: String = "Artist $id",
        lastPlayedAtMs: Long? = null,
    ) = DailyMixCandidate(song = song(id, artist), lastPlayedAtMs = lastPlayedAtMs)

    private fun profile(songAffinity: Map<String, Double>) = TasteProfile(
        computedAtMs = now,
        affinity = AffinityScores(songAffinity = songAffinity),
    )

    // --- veto / eligibility -----------------------------------------------------------------

    @Test
    fun `not interested ids never make it into the mix`() {
        val mix = buildDailyMix(
            profile = profile(mapOf("a" to 5.0, "b" to 4.0)),
            candidates = listOf(candidate("a", lastPlayedAtMs = fresh), candidate("b", lastPlayedAtMs = fresh)),
            notInterestedIds = setOf("a"),
            nowMs = now,
        )

        assertEquals(listOf("b"), mix.map { it.youtubeId })
    }

    @Test
    fun `negative affinity tracks are dropped`() {
        val mix = buildDailyMix(
            profile = profile(mapOf("liked" to 2.0, "disliked" to -1.0)),
            candidates = listOf(
                candidate("disliked", lastPlayedAtMs = fresh),
                candidate("liked", lastPlayedAtMs = fresh),
            ),
            notInterestedIds = emptySet(),
            nowMs = now,
        )

        assertEquals(listOf("liked"), mix.map { it.youtubeId })
    }

    @Test
    fun `duplicate candidates keep their first occurrence only`() {
        val mix = buildDailyMix(
            profile = profile(mapOf("a" to 1.0)),
            candidates = listOf(
                candidate("a", lastPlayedAtMs = fresh),
                candidate("a", lastPlayedAtMs = fresh),
            ),
            notInterestedIds = emptySet(),
            nowMs = now,
        )

        assertEquals(listOf("a"), mix.map { it.youtubeId })
    }

    // --- anchor -----------------------------------------------------------------------------

    @Test
    fun `the opener is the highest affinity familiar track outside the fatigue window`() {
        val mix = buildDailyMix(
            profile = profile(mapOf("top" to 9.0, "mid" to 5.0, "low" to 1.0)),
            candidates = listOf(
                candidate("low", lastPlayedAtMs = fresh),
                // Highest affinity of all, but played an hour ago: not the anchor.
                candidate("top", lastPlayedAtMs = fatigued),
                candidate("mid", lastPlayedAtMs = fresh),
            ),
            notInterestedIds = emptySet(),
            nowMs = now,
        )

        assertEquals("mid", mix.first().youtubeId)
    }

    // --- fatigue ----------------------------------------------------------------------------

    @Test
    fun `tracks played within 48h are excluded while the pool is rich enough`() {
        val richPool = (1..10).map { candidate("fresh$it", lastPlayedAtMs = fresh) }
        val mix = buildDailyMix(
            profile = profile((1..10).associate { "fresh$it" to 1.0 } + ("tired" to 5.0)),
            candidates = richPool + candidate("tired", lastPlayedAtMs = fatigued),
            notInterestedIds = emptySet(),
            nowMs = now,
            targetSize = 10,
        )

        assertFalse("tired" in mix.map { it.youtubeId })
    }

    @Test
    fun `a poor pool degrades to fatigued tracks instead of an empty mix`() {
        val mix = buildDailyMix(
            profile = profile(mapOf("a" to 3.0, "b" to 2.0)),
            candidates = listOf(
                candidate("a", lastPlayedAtMs = fatigued),
                candidate("b", lastPlayedAtMs = fatigued),
            ),
            notInterestedIds = emptySet(),
            nowMs = now,
        )

        assertEquals(listOf("a", "b"), mix.map { it.youtubeId })
    }

    // --- familiar / new balance -------------------------------------------------------------

    @Test
    fun `the mix is roughly 70 percent familiar`() {
        val familiar = (1..30).map { candidate("f$it", lastPlayedAtMs = fresh) }
        val new = (1..30).map { candidate("n$it") }
        val mix = buildDailyMix(
            profile = profile((1..30).associate { "f$it" to (31.0 - it) }),
            candidates = new + familiar,
            notInterestedIds = emptySet(),
            nowMs = now,
        )

        assertEquals(DAILY_MIX_TARGET_SIZE, mix.size)
        val familiarCount = mix.count { it.youtubeId.startsWith("f") }
        val ratio = familiarCount.toDouble() / mix.size
        assertTrue("familiar ratio was $ratio", ratio in 0.65..0.8)
    }

    @Test
    fun `two new tracks never play back to back`() {
        val familiar = (1..6).map { candidate("f$it", lastPlayedAtMs = fresh) }
        val new = (1..20).map { candidate("n$it") }
        val mix = buildDailyMix(
            profile = profile((1..6).associate { "f$it" to 1.0 }),
            candidates = new + familiar,
            notInterestedIds = emptySet(),
            nowMs = now,
        )

        mix.zipWithNext().forEach { (first, second) ->
            assertFalse(
                "two new tracks in a row: ${first.youtubeId} -> ${second.youtubeId}",
                first.youtubeId.startsWith("n") && second.youtubeId.startsWith("n"),
            )
        }
    }

    @Test
    fun `the mix ends short rather than chaining discoveries`() {
        // One familiar track, plenty of new ones: after "f new f? none" the chain must stop.
        val mix = buildDailyMix(
            profile = profile(mapOf("f1" to 1.0)),
            candidates = listOf(candidate("f1", lastPlayedAtMs = fresh)) + (1..10).map { candidate("n$it") },
            notInterestedIds = emptySet(),
            nowMs = now,
        )

        mix.map { it.youtubeId }.zipWithNext().forEach { (first, second) ->
            assertFalse(first.startsWith("n") && second.startsWith("n"))
        }
        assertTrue(mix.size < DAILY_MIX_TARGET_SIZE)
    }

    // --- artist window ----------------------------------------------------------------------

    @Test
    fun `an artist does not repeat within the sliding window when the pool allows it`() {
        val candidates = (1..40).map { index ->
            candidate("s$index", artist = "Artist ${index % 10}", lastPlayedAtMs = fresh)
        }
        val mix = buildDailyMix(
            profile = profile((1..40).associate { "s$it" to (41.0 - it) }),
            candidates = candidates,
            notInterestedIds = emptySet(),
            nowMs = now,
        )

        mix.map { artistKey(it.artist) }.windowed(DAILY_MIX_ARTIST_WINDOW).forEach { window ->
            assertEquals("artist repeated in $window", window.size, window.distinct().size)
        }
    }

    @Test
    fun `the artist window is relaxed when only one artist remains`() {
        val candidates = (1..8).map { candidate("s$it", artist = "Same Artist", lastPlayedAtMs = fresh) }
        val mix = buildDailyMix(
            profile = profile((1..8).associate { "s$it" to 1.0 }),
            candidates = candidates,
            notInterestedIds = emptySet(),
            nowMs = now,
            targetSize = 8,
        )

        assertEquals(8, mix.size)
    }

    // --- size and helpers -------------------------------------------------------------------

    @Test
    fun `the mix never exceeds the target size`() {
        val candidates = (1..100).map { candidate("s$it", lastPlayedAtMs = fresh) }
        val mix = buildDailyMix(
            profile = profile((1..100).associate { "s$it" to 1.0 }),
            candidates = candidates,
            notInterestedIds = emptySet(),
            nowMs = now,
        )

        assertEquals(DAILY_MIX_TARGET_SIZE, mix.size)
    }

    @Test
    fun `blank ids and empty inputs yield an empty mix`() {
        assertTrue(
            buildDailyMix(
                profile = profile(emptyMap()),
                candidates = listOf(candidate("")),
                notInterestedIds = emptySet(),
                nowMs = now,
            ).isEmpty()
        )
    }

    @Test
    fun `epoch day follows the local timezone`() {
        // 2023-11-14 22:13:20 UTC.
        val utcDay = dailyMixEpochDay(now, ZoneId.of("UTC"))
        // Same instant is already the next day in Tokyo.
        val tokyoDay = dailyMixEpochDay(now, ZoneId.of("Asia/Tokyo"))

        assertEquals(utcDay + 1, tokyoDay)
    }
}
