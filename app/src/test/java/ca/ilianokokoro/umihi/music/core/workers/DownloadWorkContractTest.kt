package ca.ilianokokoro.umihi.music.core.workers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadWorkContractTest {
    @Test
    fun `playlist and song identities round trip through work tags`() {
        val tags = setOf(
            DownloadWorkContract.TAG_ALL,
            "${DownloadWorkContract.TAG_SONG_PREFIX}song-42",
            "${DownloadWorkContract.TAG_PARENT_PLAYLIST_PREFIX}playlist-7",
        )

        assertEquals("song-42", DownloadWorkContract.songId(tags))
        assertEquals("playlist-7", DownloadWorkContract.playlistId(tags))
    }

    @Test
    fun `unrelated work cannot be mistaken for a download`() {
        assertNull(DownloadWorkContract.songId(setOf("sync")))
        assertNull(DownloadWorkContract.playlistId(setOf("sync")))
    }
}
