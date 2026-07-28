package ca.ilianokokoro.umihi.music.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the queue-entry identity of [Song]. `equals`/`hashCode` used to be derived
 * from a random per-instance `uid` folded through `hashCode()`, so two entries of the same track
 * were never equal and hash collisions could declare unrelated songs equal.
 */
class SongTest {

    @Test
    fun `two entries with same youtubeId and uid are equal`() {
        val a = Song(youtubeId = "abc", uid = "uid-1")
        val b = Song(youtubeId = "abc", uid = "uid-1", title = "different title")

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `same track in two distinct queue slots are not equal`() {
        val a = Song(youtubeId = "abc", uid = "uid-1")
        val b = Song(youtubeId = "abc", uid = "uid-2")

        assertNotEquals(a, b)
    }

    @Test
    fun `different tracks are not equal`() {
        val a = Song(youtubeId = "abc", uid = "uid-1")
        val b = Song(youtubeId = "xyz", uid = "uid-1")

        assertNotEquals(a, b)
    }

    @Test
    fun `equals and hashCode are consistent`() {
        val a = Song(youtubeId = "abc", uid = "uid-1")
        val b = Song(youtubeId = "abc", uid = "uid-1")

        // equal objects MUST share a hashCode (the previous impl violated this indirectly).
        if (a == b) {
            assertEquals(a.hashCode(), b.hashCode())
        }
    }

    @Test
    fun `isSameYoutubeSong compares only the track id`() {
        val a = Song(youtubeId = "abc", uid = "uid-1")
        val b = Song(youtubeId = "abc", uid = "uid-2")
        val c = Song(youtubeId = "xyz", uid = "uid-1")

        assertTrue(a.isSameYoutubeSong(b))
        assertFalse(a.isSameYoutubeSong(c))
    }

    @Test
    fun `default uid is unique per instance`() {
        val a = Song(youtubeId = "abc")
        val b = Song(youtubeId = "abc")

        assertNotEquals(a.uid, b.uid)
        assertNotEquals(a, b)
    }

    @Test
    fun `playback URL markers preserve video resolution intent`() {
        val sourceVideo = Song.createFromYoutubeUrl(
            "https://www.youtube.com/watch?v=video-id&umihiSourceVideo=1"
        )
        val forcedVideo = Song.createFromYoutubeUrl(
            "https://www.youtube.com/watch?v=video-id&umihiForceVideo=1"
        )

        assertEquals("video-id", sourceVideo.youtubeId)
        assertTrue(sourceVideo.isVideo)
        assertFalse(sourceVideo.forceVideo)
        assertTrue(forcedVideo.isVideo)
        assertTrue(forcedVideo.forceVideo)
    }

    @Test
    fun `updating like status preserves transient navigation and playback metadata`() {
        val original = Song(youtubeId = "abc", isLiked = false).also {
            it.albumId = "album"
            it.albumName = "Album"
            it.artistId = "artist"
            it.isVideo = true
            it.forceVideo = true
            it.viewCount = "1M views"
            it.trackNumber = 3
            it.discNumber = 2
        }

        val liked = original.withLikeStatus(true)

        assertEquals(true, liked.isLiked)
        assertEquals("album", liked.albumId)
        assertEquals("Album", liked.albumName)
        assertEquals("artist", liked.artistId)
        assertTrue(liked.isVideo)
        assertTrue(liked.forceVideo)
        assertEquals("1M views", liked.viewCount)
        assertEquals(3, liked.trackNumber)
        assertEquals(2, liked.discNumber)
    }

    @Test
    fun `album context fills artwork and preserves transient metadata`() {
        val song = Song(
            youtubeId = "abc",
            title = "Track",
            thumbnailHref = "",
        ).also {
            it.artistId = "artist"
            it.trackNumber = 4
        }

        val queued = song.withAlbumContext(
            albumId = "MPRE_album",
            albumName = "The Album",
            artworkHref = "https://img/cover.jpg",
        )

        assertEquals("MPRE_album", queued.albumId)
        assertEquals("The Album", queued.albumName)
        assertEquals("https://img/cover.jpg", queued.thumbnailHref)
        assertEquals("artist", queued.artistId)
        assertEquals(4, queued.trackNumber)
    }
}
