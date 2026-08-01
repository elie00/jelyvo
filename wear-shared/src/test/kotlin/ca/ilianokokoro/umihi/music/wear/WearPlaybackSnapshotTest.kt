package ca.ilianokokoro.umihi.music.wear

import org.junit.Assert.assertEquals
import org.junit.Test

class WearPlaybackSnapshotTest {
    @Test
    fun estimatedPosition_advancesOnlyWhilePlayingAndCapsAtDuration() {
        val playing = WearPlaybackSnapshot(
            positionMs = 1_000,
            durationMs = 5_000,
            isPlaying = true,
            updatedAtMs = 10_000,
        )
        assertEquals(3_500, playing.estimatedPositionMs(12_500))
        assertEquals(5_000, playing.estimatedPositionMs(20_000))

        val paused = playing.copy(isPlaying = false)
        assertEquals(1_000, paused.estimatedPositionMs(20_000))
    }
}
