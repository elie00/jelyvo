package ca.ilianokokoro.umihi.music.core.reco

import ca.ilianokokoro.umihi.music.core.playback.PlayEndReason
import ca.ilianokokoro.umihi.music.models.PlayEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TasteProfileTest {

    private val now = 1_700_000_000_000L
    private val dayMs = 24L * 60 * 60 * 1000

    private fun event(
        youtubeId: String,
        playedAt: Long = now,
        endReason: PlayEndReason = PlayEndReason.COMPLETED,
        completionRatio: Float = if (endReason == PlayEndReason.COMPLETED) 1f else 0.1f,
    ) = PlayEvent(
        youtubeId = youtubeId,
        playedAt = playedAt,
        durationListenedMs = 100_000,
        trackDurationMs = 100_000,
        completionRatio = completionRatio,
        endReason = endReason.name,
        hourOfDay = 12,
        dayOfWeek = 1,
        audioOutput = null,
        sourceContext = null,
    )

    // --- tasteDecay ---

    @Test
    fun `decay is 1 for a fresh event and one half after the half-life`() {
        assertEquals(1.0, tasteDecay(now, now), 1e-9)
        assertEquals(0.5, tasteDecay(now - 30 * dayMs, now), 1e-9)
        assertEquals(0.25, tasteDecay(now - 60 * dayMs, now), 1e-9)
    }

    @Test
    fun `decay clamps future timestamps to no boost`() {
        assertEquals(1.0, tasteDecay(now + dayMs, now), 1e-9)
    }

    // --- artistKey ---

    @Test
    fun `artist key folds case and accents`() {
        assertEquals(artistKey("mylene farmer"), artistKey("  Mylène Farmer "))
    }

    // --- affinityScores ---

    @Test
    fun `completed listen scores positive, early skip negative`() {
        val scores = affinityScores(
            events = listOf(
                event("good", endReason = PlayEndReason.COMPLETED),
                event("bad", endReason = PlayEndReason.SKIPPED_EARLY),
            ),
            likedIds = emptySet(),
            notInterestedIds = emptySet(),
            artistByYoutubeId = emptyMap(),
            nowMs = now,
        )
        assertEquals(TASTE_WEIGHT_COMPLETED, scores.songAffinity.getValue("good"), 1e-9)
        assertEquals(TASTE_WEIGHT_SKIPPED_EARLY, scores.songAffinity.getValue("bad"), 1e-9)
    }

    @Test
    fun `a like adds its full weight without decay`() {
        val scores = affinityScores(
            events = emptyList(),
            likedIds = setOf("liked"),
            notInterestedIds = emptySet(),
            artistByYoutubeId = emptyMap(),
            nowMs = now,
        )
        assertEquals(TASTE_WEIGHT_LIKE, scores.songAffinity.getValue("liked"), 1e-9)
    }

    @Test
    fun `voluntary replay adds the replay bonus on the second play`() {
        val scores = affinityScores(
            events = listOf(
                event("song", playedAt = now - 1000),
                event("song", playedAt = now),
            ),
            likedIds = emptySet(),
            notInterestedIds = emptySet(),
            artistByYoutubeId = emptyMap(),
            nowMs = now,
        )
        // Two completions + one replay bonus, all with negligible decay.
        assertEquals(
            2 * TASTE_WEIGHT_COMPLETED + TASTE_WEIGHT_REPLAY,
            scores.songAffinity.getValue("song"),
            1e-6,
        )
    }

    @Test
    fun `an early-skipped replay earns no replay bonus`() {
        val scores = affinityScores(
            events = listOf(
                event("song", playedAt = now - 1000),
                event("song", playedAt = now, endReason = PlayEndReason.SKIPPED_EARLY),
            ),
            likedIds = emptySet(),
            notInterestedIds = emptySet(),
            artistByYoutubeId = emptyMap(),
            nowMs = now,
        )
        assertEquals(
            TASTE_WEIGHT_COMPLETED + TASTE_WEIGHT_SKIPPED_EARLY,
            scores.songAffinity.getValue("song"),
            1e-6,
        )
    }

    @Test
    fun `old events decay by their age`() {
        val scores = affinityScores(
            events = listOf(event("song", playedAt = now - 30 * dayMs)),
            likedIds = emptySet(),
            notInterestedIds = emptySet(),
            artistByYoutubeId = emptyMap(),
            nowMs = now,
        )
        assertEquals(TASTE_WEIGHT_COMPLETED * 0.5, scores.songAffinity.getValue("song"), 1e-6)
    }

    @Test
    fun `not interested is a veto, dropping events and likes entirely`() {
        val scores = affinityScores(
            events = listOf(event("vetoed"), event("kept")),
            likedIds = setOf("vetoed"),
            notInterestedIds = setOf("vetoed"),
            artistByYoutubeId = emptyMap(),
            nowMs = now,
        )
        assertFalse("vetoed" in scores.songAffinity)
        assertTrue("kept" in scores.songAffinity)
    }

    @Test
    fun `artist affinity sums song scores under the normalized name`() {
        val scores = affinityScores(
            events = listOf(event("a"), event("b")),
            likedIds = emptySet(),
            notInterestedIds = emptySet(),
            artistByYoutubeId = mapOf("a" to "Mylène Farmer", "b" to "mylene farmer"),
            nowMs = now,
        )
        assertEquals(
            2 * TASTE_WEIGHT_COMPLETED,
            scores.artistAffinity.getValue(artistKey("Mylène Farmer")),
            1e-6,
        )
    }

    @Test
    fun `mixed signals combine likes, skips and completions`() {
        val scores = affinityScores(
            events = listOf(
                event("song", endReason = PlayEndReason.COMPLETED),
                event("song", playedAt = now + 1, endReason = PlayEndReason.SKIPPED_LATE),
            ),
            likedIds = setOf("song"),
            notInterestedIds = emptySet(),
            artistByYoutubeId = emptyMap(),
            nowMs = now,
        )
        // Completion + late skip (still a non-rejected replay) + replay bonus + like.
        assertEquals(
            TASTE_WEIGHT_COMPLETED + TASTE_WEIGHT_SKIPPED_LATE + TASTE_WEIGHT_REPLAY +
                    TASTE_WEIGHT_LIKE,
            scores.songAffinity.getValue("song"),
            1e-6,
        )
    }

    @Test
    fun `unclassified end with a near-full ratio counts as a completion`() {
        val scores = affinityScores(
            events = listOf(
                event("song", endReason = PlayEndReason.UNKNOWN, completionRatio = 0.9f),
            ),
            likedIds = emptySet(),
            notInterestedIds = emptySet(),
            artistByYoutubeId = emptyMap(),
            nowMs = now,
        )
        assertEquals(TASTE_WEIGHT_COMPLETED, scores.songAffinity.getValue("song"), 1e-6)
    }

    // --- buildTasteProfile ---

    @Test
    fun `full profile carries the event count and computation time`() {
        val profile = buildTasteProfile(
            events = listOf(event("a"), event("b")),
            likedIds = emptySet(),
            notInterestedIds = emptySet(),
            artistByYoutubeId = mapOf("a" to "Artist"),
            nowMs = now,
        )
        assertEquals(2, profile.eventCount)
        assertEquals(now, profile.computedAtMs)
        assertTrue(artistKey("Artist") in profile.affinity.artistAffinity)
    }
}
