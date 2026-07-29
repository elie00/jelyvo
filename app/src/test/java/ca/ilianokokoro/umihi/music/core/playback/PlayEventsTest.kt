package ca.ilianokokoro.umihi.music.core.playback

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset

class PlayEventsTest {

    // --- classifyPlayEnd ---

    @Test
    fun `instant skip is an early skip`() {
        assertEquals(PlayEndReason.SKIPPED_EARLY, classifyPlayEnd(2_000, 200_000))
    }

    @Test
    fun `under 30s and under half of a short track is an early skip`() {
        // 25s of a 60s track: under both thresholds.
        assertEquals(PlayEndReason.SKIPPED_EARLY, classifyPlayEnd(25_000, 60_000))
    }

    @Test
    fun `under 30s but over half the track is a late skip, not early`() {
        // 25s of a 40s track: 62% listened, the short listen was most of the song.
        assertEquals(PlayEndReason.SKIPPED_LATE, classifyPlayEnd(25_000, 40_000))
    }

    @Test
    fun `over 30s but under 80 percent is a late skip`() {
        assertEquals(PlayEndReason.SKIPPED_LATE, classifyPlayEnd(120_000, 200_000))
    }

    @Test
    fun `80 percent listened is completed`() {
        assertEquals(PlayEndReason.COMPLETED, classifyPlayEnd(160_000, 200_000))
    }

    @Test
    fun `full listen is completed`() {
        assertEquals(PlayEndReason.COMPLETED, classifyPlayEnd(200_000, 200_000))
    }

    @Test
    fun `unknown duration classifies as unknown`() {
        assertEquals(PlayEndReason.UNKNOWN, classifyPlayEnd(45_000, 0))
        assertEquals(PlayEndReason.UNKNOWN, classifyPlayEnd(45_000, -1))
    }

    // --- classifyPlayEndOnServiceDeath ---

    @Test
    fun `service death mid-track is app killed, not a skip`() {
        assertEquals(PlayEndReason.APP_KILLED, classifyPlayEndOnServiceDeath(45_000, 200_000))
        assertEquals(PlayEndReason.APP_KILLED, classifyPlayEndOnServiceDeath(2_000, 200_000))
        assertEquals(PlayEndReason.APP_KILLED, classifyPlayEndOnServiceDeath(45_000, 0))
    }

    @Test
    fun `service death after a full listen stays completed`() {
        assertEquals(PlayEndReason.COMPLETED, classifyPlayEndOnServiceDeath(190_000, 200_000))
    }

    // --- playCompletionRatio ---

    @Test
    fun `completion ratio is listened over duration`() {
        assertEquals(0.5f, playCompletionRatio(100_000, 200_000), 0f)
    }

    @Test
    fun `completion ratio clamps overshoot and unknown duration`() {
        // Repeat-one style overshoot never exceeds 1.
        assertEquals(1f, playCompletionRatio(250_000, 200_000), 0f)
        assertEquals(0f, playCompletionRatio(100_000, 0), 0f)
    }

    // --- ListenStopwatch ---

    @Test
    fun `stopwatch only counts while running`() {
        var watch = ListenStopwatch()
        assertEquals(0L, watch.totalMs(1_000))

        watch = watch.started(1_000)
        assertEquals(4_000L, watch.totalMs(5_000))

        watch = watch.stopped(5_000)
        // Paused: time keeps passing but the counter does not.
        assertEquals(4_000L, watch.totalMs(60_000))

        watch = watch.started(60_000)
        assertEquals(7_000L, watch.totalMs(63_000))
    }

    @Test
    fun `redundant starts and stops are idempotent`() {
        var watch = ListenStopwatch().started(1_000).started(2_000)
        // Second start is ignored, counting still runs from the first.
        assertEquals(3_000L, watch.totalMs(4_000))

        watch = watch.stopped(4_000).stopped(9_000)
        assertEquals(3_000L, watch.totalMs(9_000))
    }

    @Test
    fun `clock going backwards never yields negative time`() {
        val watch = ListenStopwatch().started(10_000)
        assertEquals(0L, watch.totalMs(5_000))
        assertEquals(0L, watch.stopped(5_000).accumulatedMs)
    }

    // --- time-of-day helpers ---

    @Test
    fun `hour and day are computed in the given zone`() {
        // 2026-07-22 is a Wednesday; 22:30 UTC.
        val epochMs = 1_784_759_400_000L
        assertEquals(22, hourOfDay(epochMs, ZoneOffset.UTC))
        assertEquals(3, dayOfWeekIso(epochMs, ZoneOffset.UTC))
        // Two hours east it is already Thursday, 00:30.
        assertEquals(0, hourOfDay(epochMs, ZoneOffset.ofHours(2)))
        assertEquals(4, dayOfWeekIso(epochMs, ZoneOffset.ofHours(2)))
    }
}
