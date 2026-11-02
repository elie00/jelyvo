package ca.ilianokokoro.umihi.music.core.scrobbling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ScrobbleTrackerTest {
    private val threeMinutes = 180_000L

    private fun track(durationMs: Long = threeMinutes) = ScrobbleTrack(
        artist = "Artist",
        title = "Title",
        durationMs = durationMs,
        startedAtMs = 0L,
    )

    @Test
    fun `tracks of thirty seconds or less never count`() {
        assertNull(ScrobbleRules.requiredListenMs(30_000L))
        assertEquals(15_500L, ScrobbleRules.requiredListenMs(31_000L))
    }

    @Test
    fun `long tracks need four minutes at most`() {
        assertEquals(240_000L, ScrobbleRules.requiredListenMs(3_600_000L))
    }

    @Test
    fun `listen becomes due at half the track`() {
        val tracker = ScrobbleTracker()
        tracker.onTrackChanged(track(), isPlaying = true, nowMs = 0L)

        assertNull(tracker.takeDueListen(89_999L))
        assertEquals(1L, tracker.millisUntilDue(89_999L))
        assertNotNull(tracker.takeDueListen(90_000L))
    }

    @Test
    fun `listen is only taken once per play`() {
        val tracker = ScrobbleTracker()
        tracker.onTrackChanged(track(), isPlaying = true, nowMs = 0L)

        assertNotNull(tracker.takeDueListen(90_000L))
        assertNull(tracker.takeDueListen(120_000L))
        assertNull(tracker.millisUntilDue(120_000L))
    }

    @Test
    fun `pauses do not count as listening`() {
        val tracker = ScrobbleTracker()
        tracker.onTrackChanged(track(), isPlaying = true, nowMs = 0L)
        tracker.onIsPlayingChanged(false, nowMs = 60_000L)

        assertNull(tracker.takeDueListen(500_000L))
        assertNull(tracker.millisUntilDue(500_000L))

        tracker.onIsPlayingChanged(true, nowMs = 500_000L)
        assertEquals(30_000L, tracker.millisUntilDue(500_000L))
        assertNotNull(tracker.takeDueListen(530_000L))
    }

    @Test
    fun `no listen until the duration is known`() {
        val tracker = ScrobbleTracker()
        tracker.onTrackChanged(track(durationMs = 0L), isPlaying = true, nowMs = 0L)

        assertNull(tracker.millisUntilDue(100_000L))
        assertNull(tracker.takeDueListen(100_000L))

        tracker.onDurationKnown(threeMinutes)
        assertNotNull(tracker.takeDueListen(100_000L))
    }

    @Test
    fun `now playing waits for playback and fires once`() {
        val tracker = ScrobbleTracker()
        tracker.onTrackChanged(track(), isPlaying = false, nowMs = 0L)
        assertNull(tracker.takeNowPlaying())

        tracker.onIsPlayingChanged(true, nowMs = 1_000L)
        assertNotNull(tracker.takeNowPlaying())
        assertNull(tracker.takeNowPlaying())
    }

    @Test
    fun `changing track starts a fresh count`() {
        val tracker = ScrobbleTracker()
        tracker.onTrackChanged(track(), isPlaying = true, nowMs = 0L)
        tracker.onTrackChanged(track(), isPlaying = true, nowMs = 80_000L)

        assertNull(tracker.takeDueListen(100_000L))
        assertNotNull(tracker.takeDueListen(170_000L))
    }

    @Test
    fun `clearing the track stops everything`() {
        val tracker = ScrobbleTracker()
        tracker.onTrackChanged(track(), isPlaying = true, nowMs = 0L)
        tracker.onTrackChanged(null, isPlaying = false, nowMs = 1_000L)

        assertNull(tracker.takeNowPlaying())
        assertNull(tracker.millisUntilDue(100_000L))
        assertNull(tracker.takeDueListen(100_000L))
    }
}
