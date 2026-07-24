package ca.ilianokokoro.umihi.music.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LocalPlaylistSongTest {

    @Test
    fun `queue song conversion preserves playable metadata`() {
        val queueSong = Song(
            youtubeId = "track-id",
            title = "Track title",
            artist = "Artist",
            duration = "3:42",
            thumbnailHref = "https://example.com/remote.jpg",
            thumbnailPath = "/data/local.jpg",
            uid = "queue-slot",
        )

        val savedSong = LocalPlaylistSong.fromSong(queueSong)

        assertEquals(queueSong.youtubeId, savedSong.id)
        assertEquals(queueSong.title, savedSong.title)
        assertEquals(queueSong.artist, savedSong.artist)
        assertEquals(queueSong.duration, savedSong.duration)
        assertEquals(queueSong.thumbnailPath, savedSong.thumbnailHref)
        assertNotEquals(queueSong.uid, savedSong.uid)
    }

    @Test
    fun `duplicate queue entries remain distinct playlist rows`() {
        val first = LocalPlaylistSong.fromSong(
            Song(youtubeId = "same-track", uid = "queue-slot-1")
        )
        val second = LocalPlaylistSong.fromSong(
            Song(youtubeId = "same-track", uid = "queue-slot-2")
        )

        assertEquals(first.id, second.id)
        assertNotEquals(first.uid, second.uid)
    }
}
