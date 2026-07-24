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
}
