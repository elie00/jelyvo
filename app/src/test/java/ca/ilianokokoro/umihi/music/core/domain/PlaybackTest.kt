package ca.ilianokokoro.umihi.music.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackTest {
    private val queue = PlaybackQueue(
        entries = listOf(
            QueueEntry(entryId = "slot-1", track = Track(id = "first")),
            QueueEntry(entryId = "slot-2", track = Track(id = "second")),
            QueueEntry(entryId = "slot-3", track = Track(id = "third")),
        ),
        currentIndex = 0,
    )

    @Test
    fun `up next keeps queue indices after current entry`() {
        val result = queue.upNext()

        assertEquals(listOf(1, 2), result.map(IndexedValue<QueueEntry>::index))
        assertEquals(listOf("second", "third"), result.map { it.value.track.id })
    }

    @Test
    fun `last or invalid current entry has no up next entries`() {
        assertEquals(emptyList<IndexedValue<QueueEntry>>(), queue.copy(currentIndex = 2).upNext())
        assertEquals(emptyList<IndexedValue<QueueEntry>>(), queue.copy(currentIndex = -1).upNext())
        assertEquals(emptyList<IndexedValue<QueueEntry>>(), queue.copy(currentIndex = 3).upNext())
    }

    @Test
    fun `current entry resolves inside bounds only`() {
        assertEquals("slot-1", queue.current?.entryId)
        assertNull(queue.copy(currentIndex = -1).current)
        assertNull(queue.copy(currentIndex = 3).current)
    }

    @Test
    fun `progress fraction is clamped and safe on unknown duration`() {
        assertEquals(0.5f, PlaybackProgress(positionMs = 50, durationMs = 100).fraction)
        assertEquals(1f, PlaybackProgress(positionMs = 150, durationMs = 100).fraction)
        assertEquals(0f, PlaybackProgress(positionMs = 10, durationMs = 0).fraction)
    }

    @Test
    fun `relative seek clamps to track bounds`() {
        assertEquals(0L, relativeSeekTarget(positionMs = 5_000, durationMs = 60_000, deltaMs = -10_000))
        assertEquals(60_000L, relativeSeekTarget(positionMs = 55_000, durationMs = 60_000, deltaMs = 10_000))
        assertEquals(35_000L, relativeSeekTarget(positionMs = 25_000, durationMs = 60_000, deltaMs = 10_000))
    }

    @Test
    fun `relative seek handles unknown duration and overflow`() {
        assertEquals(15_000L, relativeSeekTarget(positionMs = 5_000, durationMs = 0, deltaMs = 10_000))
        assertEquals(
            Long.MAX_VALUE,
            relativeSeekTarget(
                positionMs = Long.MAX_VALUE - 1,
                durationMs = 0,
                deltaMs = 10_000,
            )
        )
    }
}
