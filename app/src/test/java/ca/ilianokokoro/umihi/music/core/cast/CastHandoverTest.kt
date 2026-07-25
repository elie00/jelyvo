package ca.ilianokokoro.umihi.music.core.cast

import ca.ilianokokoro.umihi.music.models.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CastHandoverTest {
    private fun song(id: String, local: Boolean = false): Song =
        Song(youtubeId = id, title = id).also { it.isLocal = local }

    @Test
    fun `handover keeps order and remaps the current stream`() {
        val plan = planCastHandover(
            songs = listOf(song("a"), song("local:1", local = true), song("b")),
            currentIndex = 2,
            positionMs = 12_345L,
        )

        assertEquals(listOf("a", "b"), plan.songs.map { it.youtubeId })
        assertEquals(1, plan.startIndex)
        assertEquals(12_345L, plan.startPositionMs)
        assertEquals(1, plan.skippedLocalCount)
    }

    @Test
    fun `a local current item advances to the next castable track`() {
        val plan = planCastHandover(
            songs = listOf(song("a"), song("local:1", local = true), song("b")),
            currentIndex = 1,
            positionMs = 9_000L,
        )

        assertEquals(1, plan.startIndex)
        assertEquals(0L, plan.startPositionMs)
    }

    @Test
    fun `a trailing local item falls back to the closest stream`() {
        val plan = planCastHandover(
            songs = listOf(song("a"), song("local:1", local = true)),
            currentIndex = 1,
            positionMs = 9_000L,
        )

        assertEquals(0, plan.startIndex)
        assertEquals(0L, plan.startPositionMs)
    }

    @Test
    fun `an all-local queue cannot start a cast session`() {
        val plan = planCastHandover(
            songs = listOf(song("local:1", local = true)),
            currentIndex = 0,
            positionMs = 100L,
        )

        assertTrue(plan.songs.isEmpty())
        assertEquals(-1, plan.startIndex)
        assertEquals(1, plan.skippedLocalCount)
    }

    @Test
    fun `a downloaded youtube track remains castable`() {
        val downloaded = Song(
            youtubeId = "downloaded",
            audioFilePath = "/private/audio",
            thumbnailPath = "/private/art",
        )

        assertEquals(
            listOf(downloaded),
            planCastHandover(listOf(downloaded), 0, 50L).songs,
        )
    }

    @Test
    fun `resolution window is bounded and handles an unset index`() {
        assertEquals(listOf(0, 1, 2), resolutionWindow(-1, size = 5).toList())
        assertEquals(listOf(3, 4), resolutionWindow(3, size = 5).toList())
        assertTrue(resolutionWindow(0, size = 0).isEmpty())
    }
}
