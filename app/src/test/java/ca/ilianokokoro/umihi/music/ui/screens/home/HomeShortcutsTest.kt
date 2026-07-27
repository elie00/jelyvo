package ca.ilianokokoro.umihi.music.ui.screens.home

import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.models.TrackListeningStat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeShortcutsTest {

    private fun stat(id: String, playCount: Int = 5) = TrackListeningStat(
        youtubeId = id,
        title = "Title $id",
        artist = "Artist $id",
        thumbnailHref = "",
        duration = "3:00",
        isExplicit = false,
        playCount = playCount,
        lastPlayedAt = 0L,
    )

    private fun song(id: String) = Song(
        youtubeId = id,
        title = "Title $id",
        artist = "Artist $id",
        thumbnailHref = "",
        duration = "3:00",
    )

    @Test
    fun `top tracks come first then recents fill the grid`() {
        val shortcuts = buildHomeShortcuts(
            topTracks = listOf(stat("a"), stat("b")),
            recentlyPlayed = listOf(song("c"), song("d"), song("e"), song("f")),
        )

        assertEquals(listOf("a", "b", "c", "d", "e", "f"), shortcuts.map { it.youtubeId })
    }

    @Test
    fun `duplicates between tops and recents collapse to one tile`() {
        val shortcuts = buildHomeShortcuts(
            topTracks = listOf(stat("a"), stat("b")),
            recentlyPlayed = listOf(song("b"), song("a"), song("c"), song("d")),
        )

        assertEquals(listOf("a", "b", "c", "d"), shortcuts.map { it.youtubeId })
    }

    @Test
    fun `the grid is capped at eight tiles`() {
        val shortcuts = buildHomeShortcuts(
            topTracks = (1..12).map { stat("t$it") },
            recentlyPlayed = emptyList(),
        )

        assertEquals(HOME_SHORTCUTS_MAX, shortcuts.size)
    }

    @Test
    fun `an odd tile count is trimmed to full rows of two`() {
        val shortcuts = buildHomeShortcuts(
            topTracks = (1..7).map { stat("t$it") },
            recentlyPlayed = emptyList(),
        )

        assertEquals(6, shortcuts.size)
    }

    @Test
    fun `too little history hides the grid entirely`() {
        assertTrue(
            buildHomeShortcuts(
                topTracks = listOf(stat("a")),
                recentlyPlayed = listOf(song("b"), song("c")),
            ).isEmpty()
        )
        assertTrue(buildHomeShortcuts(emptyList(), emptyList()).isEmpty())
    }
}
