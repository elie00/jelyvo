package ca.ilianokokoro.umihi.music.services

import ca.ilianokokoro.umihi.music.core.playback.PlayEndReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneOffset

class PlayEventTrackerTest {

    private fun tracker() = PlayEventTracker(zone = ZoneOffset.UTC)

    @Test
    fun `first transition has nothing to finalize`() {
        val tracker = tracker()
        assertNull(
            tracker.onMediaItemTransition(
                youtubeId = "a",
                isPlaying = true,
                nowMs = 0,
                audioOutput = "SPEAKER",
                sourceContext = null,
            )
        )
    }

    @Test
    fun `transition finalizes the previous track with its listened time and context`() {
        val tracker = tracker()
        tracker.onMediaItemTransition("a", true, 0, "BLUETOOTH", "My album")
        tracker.onDurationKnown(200_000)

        val event = tracker.onMediaItemTransition("b", true, 160_000, "SPEAKER", null)!!

        assertEquals("a", event.youtubeId)
        assertEquals(0L, event.playedAt)
        assertEquals(160_000L, event.durationListenedMs)
        assertEquals(200_000L, event.trackDurationMs)
        assertEquals(0.8f, event.completionRatio, 0f)
        assertEquals(PlayEndReason.COMPLETED.name, event.endReason)
        // Context captured when the play started, not when it ended.
        assertEquals("BLUETOOTH", event.audioOutput)
        assertEquals("My album", event.sourceContext)
    }

    @Test
    fun `pauses do not count as listened time`() {
        val tracker = tracker()
        tracker.onMediaItemTransition("a", true, 0, null, null)
        tracker.onDurationKnown(200_000)
        tracker.onIsPlayingChanged(false, 10_000)
        // A long pause...
        tracker.onIsPlayingChanged(true, 500_000)

        val event = tracker.onMediaItemTransition("b", true, 505_000, null, null)!!

        assertEquals(15_000L, event.durationListenedMs)
        assertEquals(PlayEndReason.SKIPPED_EARLY.name, event.endReason)
    }

    @Test
    fun `a track that never played is not recorded`() {
        val tracker = tracker()
        // Queue restore: item becomes current but playback never starts.
        tracker.onMediaItemTransition("a", false, 0, null, null)
        assertNull(tracker.onMediaItemTransition("b", false, 60_000, null, null))
    }

    @Test
    fun `playback ended finalizes as completed`() {
        val tracker = tracker()
        tracker.onMediaItemTransition("a", true, 0, null, null)
        tracker.onDurationKnown(180_000)

        val event = tracker.onPlaybackEnded(180_000)!!

        assertEquals(PlayEndReason.COMPLETED.name, event.endReason)
        // Nothing left to finalize afterwards.
        assertNull(tracker.onServiceDestroyed(200_000))
    }

    @Test
    fun `service death mid-play is app killed`() {
        val tracker = tracker()
        tracker.onMediaItemTransition("a", true, 0, null, null)
        tracker.onDurationKnown(200_000)

        val event = tracker.onServiceDestroyed(60_000)!!

        assertEquals(PlayEndReason.APP_KILLED.name, event.endReason)
        assertEquals(60_000L, event.durationListenedMs)
    }

    @Test
    fun `unknown duration is recorded with the unknown reason`() {
        val tracker = tracker()
        tracker.onMediaItemTransition("a", true, 0, null, null)

        val event = tracker.onMediaItemTransition("b", true, 45_000, null, null)!!

        assertEquals(0L, event.trackDurationMs)
        assertEquals(0f, event.completionRatio, 0f)
        assertEquals(PlayEndReason.UNKNOWN.name, event.endReason)
    }

    @Test
    fun `hour and day come from the play start instant`() {
        val tracker = tracker()
        // 2026-07-22T22:30:00Z, a Wednesday.
        val startMs = 1_784_759_400_000L
        tracker.onMediaItemTransition("a", true, startMs, null, null)
        tracker.onDurationKnown(100_000)

        val event = tracker.onMediaItemTransition("b", true, startMs + 100_000, null, null)!!

        assertEquals(22, event.hourOfDay)
        assertEquals(3, event.dayOfWeek)
    }
}
