package ca.ilianokokoro.umihi.music.ui.screens.album

import ca.ilianokokoro.umihi.music.models.LocalPlaylistSong
import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumPlaylistMergeTest {

    private fun song(id: String) = LocalPlaylistSong(id = id, title = "Title $id")

    @Test
    fun `appends album tracks after the existing songs`() {
        val merged = mergeSongsIntoLocalPlaylist(
            existing = listOf(song("a"), song("b")),
            added = listOf(song("c"), song("d")),
        )

        assertEquals(listOf("a", "b", "c", "d"), merged.map { it.id })
    }

    @Test
    fun `skips tracks already present, keeping the playlist order`() {
        val existing = listOf(song("a"), song("b"))

        val merged = mergeSongsIntoLocalPlaylist(
            existing = existing,
            added = listOf(song("b"), song("c")),
        )

        assertEquals(listOf("a", "b", "c"), merged.map { it.id })
        // The original entries are untouched (same instances, same slots).
        assertEquals(existing, merged.take(2))
    }

    @Test
    fun `collapses duplicates inside the added list itself`() {
        val merged = mergeSongsIntoLocalPlaylist(
            existing = emptyList(),
            added = listOf(song("a"), song("a"), song("b")),
        )

        assertEquals(listOf("a", "b"), merged.map { it.id })
    }

    @Test
    fun `adding an entire album twice is a no-op`() {
        val album = listOf(song("a"), song("b"))
        val once = mergeSongsIntoLocalPlaylist(existing = emptyList(), added = album)

        val twice = mergeSongsIntoLocalPlaylist(existing = once, added = album)

        assertEquals(once.map { it.id }, twice.map { it.id })
    }
}
