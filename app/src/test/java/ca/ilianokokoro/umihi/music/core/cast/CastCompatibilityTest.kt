package ca.ilianokokoro.umihi.music.core.cast

import ca.ilianokokoro.umihi.music.models.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The whole point of keeping these rules android-free: both failures they prevent are invisible on
 * the phone. A local file handed to a receiver fails on the TV with no useful message, and a wrong
 * contentType makes the receiver refuse a load the app believes it sent correctly.
 */
class CastCompatibilityTest {

    private fun song(id: String = "dQw4w9WgXcQ", local: Boolean = false) =
        Song(youtubeId = id, title = "t", artist = "a").also { it.isLocal = local }

    @Test
    fun `a streamed youtube track can cast`() {
        assertTrue(song().isCastable())
    }

    @Test
    fun `a downloaded track can still cast, because it casts as a stream`() {
        // `downloaded` is deliberately absent from the predicate: the FILE cannot cast, the TRACK
        // can, because resolution is asked for a network stream.
        val downloaded = Song(
            youtubeId = "dQw4w9WgXcQ",
            audioFilePath = "/data/user/0/pkg/files/downloads/audio/x.webm",
            thumbnailPath = "/data/user/0/pkg/files/downloads/thumbs/x.jpg",
        )
        assertTrue(downloaded.downloaded)
        assertTrue(downloaded.isCastable())
    }

    @Test
    fun `a MediaStore track cannot cast`() {
        assertFalse(song(local = true).isCastable())
    }

    @Test
    fun `the local id prefix is rejected even when the transient flag was lost`() {
        // isLocal is @Ignore and does not survive every Bundle round-trip; the id always does.
        assertFalse(song(id = "local:42", local = false).isCastable())
    }

    @Test
    fun `a track with no id cannot cast`() {
        assertFalse(song(id = "").isCastable())
    }

    @Test
    fun `the format mime type wins and loses its codec parameters`() {
        assertEquals(
            "audio/webm",
            castContentType("audio/webm; codecs=\"opus\"", "https://r1.googlevideo.com/x")
        )
        assertEquals("audio/mp4", castContentType("AUDIO/MP4", "https://r1.googlevideo.com/x"))
    }

    @Test
    fun `the url mime parameter is the fallback`() {
        assertEquals(
            "audio/webm",
            castContentType(null, "https://r1.googlevideo.com/x?mime=audio%2Fwebm&itag=251")
        )
    }

    @Test
    fun `a non-audio type is refused rather than forwarded`() {
        // A video/* format would make the receiver try to render video for an audio-only session.
        assertEquals(
            DEFAULT_CAST_CONTENT_TYPE,
            castContentType("video/mp4", "https://r1.googlevideo.com/x")
        )
    }

    @Test
    fun `everything unknown falls back to the documented default`() {
        assertEquals(DEFAULT_CAST_CONTENT_TYPE, castContentType(null, "not a url"))
        assertEquals(DEFAULT_CAST_CONTENT_TYPE, castContentType("", "https://r1.googlevideo.com/x"))
    }

    @Test
    fun `an unresolved watch url is recognised`() {
        assertTrue(isUnresolvedWatchUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertFalse(isUnresolvedWatchUrl("https://r1.googlevideo.com/videoplayback?expire=1"))
    }
}
