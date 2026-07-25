package ca.ilianokokoro.umihi.music.models

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The mosaic cover picks its tiles from the playlist's own tracks. Blank and duplicate artwork must
 * never reach the grid: a blank href draws an empty tile, and a duplicate wastes one of the four
 * slots on a cover already shown.
 */
class LocalPlaylistCoverTest {

    private fun song(id: String, thumbnail: String) = LocalPlaylistSong(
        id = id,
        title = id,
        artist = "artist",
        thumbnailHref = thumbnail,
        duration = "3:00",
    )

    @Test
    fun `takes the first four covers in playlist order`() {
        val songs = (1..6).map { song("id$it", "https://img/$it.jpg") }

        assertEquals(
            listOf("https://img/1.jpg", "https://img/2.jpg", "https://img/3.jpg", "https://img/4.jpg"),
            songs.mosaicCoverUris(),
        )
    }

    @Test
    fun `drops blank hrefs and duplicates`() {
        val songs = listOf(
            song("a", "https://img/1.jpg"),
            song("b", ""),
            song("c", "https://img/1.jpg"),
            song("d", "   "),
            song("e", "https://img/2.jpg"),
        )

        assertEquals(listOf("https://img/1.jpg", "https://img/2.jpg"), songs.mosaicCoverUris())
    }

    @Test
    fun `an empty or artwork-less playlist yields no tiles`() {
        assertEquals(emptyList<String>(), emptyList<LocalPlaylistSong>().mosaicCoverUris())
        assertEquals(emptyList<String>(), listOf(song("a", ""), song("b", "")).mosaicCoverUris())
    }
}
