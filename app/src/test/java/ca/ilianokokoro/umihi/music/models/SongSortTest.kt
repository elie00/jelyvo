package ca.ilianokokoro.umihi.music.models

import org.junit.Assert.assertEquals
import org.junit.Test

class SongSortTest {

    private fun song(title: String, artist: String = "", duration: String = "") =
        Song(youtubeId = title, title = title, artist = artist, duration = duration)

    @Test
    fun `playlist order returns the list untouched`() {
        val songs = listOf(song("b"), song("a"), song("c"))
        assertEquals(songs, songs.sortedForPlaylist(SongSort.PLAYLIST_ORDER))
    }

    @Test
    fun `title sort ignores case and accents`() {
        val songs = listOf(song("Zoo"), song("Élan"), song("apple"))
        assertEquals(
            listOf("apple", "Élan", "Zoo"),
            songs.sortedForPlaylist(SongSort.TITLE).map { it.title }
        )
    }

    @Test
    fun `artist sort ignores case and accents`() {
        val songs = listOf(
            song("1", artist = "Zaz"),
            song("2", artist = "étienne"),
            song("3", artist = "Alpha"),
        )
        assertEquals(
            listOf("Alpha", "étienne", "Zaz"),
            songs.sortedForPlaylist(SongSort.ARTIST).map { it.artist }
        )
    }

    @Test
    fun `duration sort reads both display formats and keeps unparseable rows`() {
        val songs = listOf(
            song("long", duration = "1:02:03"),
            song("short", duration = "0:45"),
            song("unknown", duration = ""),
            song("medium", duration = "3:20"),
            song("garbage", duration = "n/a"),
        )
        // Unparseable durations fall to 0 and sort first rather than vanishing from the list.
        assertEquals(
            listOf("unknown", "garbage", "short", "medium", "long"),
            songs.sortedForPlaylist(SongSort.DURATION).map { it.title }
        )
    }

    @Test
    fun `the on-device overload sorts the same way`() {
        val songs = listOf(
            LocalPlaylistSong(id = "1", title = "Zoo"),
            LocalPlaylistSong(id = "2", title = "Élan"),
        )
        assertEquals(
            listOf("Élan", "Zoo"),
            songs.sortedForPlaylist(SongSort.TITLE).map { it.title }
        )
    }
}
