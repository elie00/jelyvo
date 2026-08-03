package ca.ilianokokoro.umihi.music.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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

    @Test
    fun queueCommand_roundTripsValidIndexAndRejectsInvalidPayload() {
        assertEquals(12, WearPlaybackContract.queueIndexFromCommand(
            WearPlaybackContract.playQueueCommand(12),
        ))
        assertEquals(null, WearPlaybackContract.queueIndexFromCommand("play_queue:nope"))
        assertEquals(null, WearPlaybackContract.queueIndexFromCommand("next"))
    }

    @Test
    fun commandResult_roundTripsErrorsContainingSeparators() {
        val payload = WearPlaybackContract.resultPayload(
            success = false,
            command = WearPlaybackContract.COMMAND_TOGGLE_LIKE,
            reason = "login|required",
        )

        assertEquals(
            WearCommandResult(false, WearPlaybackContract.COMMAND_TOGGLE_LIKE, "login|required"),
            WearPlaybackContract.parseResult(payload),
        )
    }

    @Test
    fun playbackHandoff_roundTripsQueuePositionAndPlaybackIntent() {
        val handoff = WearPlaybackHandoff(
            queue = listOf(
                WearQueueItem("first", "Premier", "Artiste A", "https://img/first"),
                WearQueueItem("second", "Deuxième", "Artiste B", "https://img/second"),
            ),
            currentIndex = 1,
            positionMs = 42_500,
            playWhenReady = true,
        )

        assertEquals(handoff, decodeWearPlaybackHandoff(handoff.encode()))
    }

    @Test
    fun playbackHandoff_rejectsEmptyQueueAndCorruptPayload() {
        assertThrows(IllegalArgumentException::class.java) {
            WearPlaybackHandoff(emptyList(), 0, 0).encode()
        }
        assertNull(decodeWearPlaybackHandoff(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun playbackHandoff_keepsCurrentItemInsideLargeQueues() {
        val queue = List(80) { index -> WearQueueItem("id-$index", "Titre $index", "", "") }

        val decoded = decodeWearPlaybackHandoff(
            WearPlaybackHandoff(queue, currentIndex = 65, positionMs = 1_000).encode(),
        )!!

        assertEquals(18, decoded.queue.size)
        assertEquals(3, decoded.currentIndex)
        assertEquals("id-65", decoded.queue[decoded.currentIndex].mediaId)
    }
}
