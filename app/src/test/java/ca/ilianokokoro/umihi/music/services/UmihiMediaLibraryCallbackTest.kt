package ca.ilianokokoro.umihi.music.services

import androidx.media3.common.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UmihiMediaLibraryCallbackTest {
    private val items = (0 until 7).map { index ->
        MediaItem.Builder().setMediaId("song-$index").build()
    }

    @Test
    fun `pagination returns requested page`() {
        val page = paginateMediaItems(items, page = 1, pageSize = 3)

        assertEquals(listOf("song-3", "song-4", "song-5"), page.map(MediaItem::mediaId))
    }

    @Test
    fun `pagination gracefully handles the final page and invalid arguments`() {
        assertEquals(
            listOf("song-6"),
            paginateMediaItems(items, page = 2, pageSize = 3).map(MediaItem::mediaId),
        )
        assertTrue(paginateMediaItems(items, page = -1, pageSize = 3).isEmpty())
        assertTrue(paginateMediaItems(items, page = 0, pageSize = 0).isEmpty())
        assertTrue(paginateMediaItems(items, page = Int.MAX_VALUE, pageSize = Int.MAX_VALUE).isEmpty())
    }
}
