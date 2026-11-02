package ca.ilianokokoro.umihi.music.core.scrobbling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.security.MessageDigest

class LastFmRequestsTest {
    private val track = ScrobbleTrack(
        artist = "Artist",
        title = "Title",
        album = "Album",
        durationMs = 200_500L,
        startedAtMs = 1_700_000_000_999L,
    )

    private fun md5(text: String) = MessageDigest.getInstance("MD5")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    @Test
    fun `signature sorts parameters and appends the secret`() {
        val signature = LastFmRequests.signature(
            mapOf("method" to "auth.getToken", "api_key" to "KEY"),
            secret = "SECRET",
        )

        assertEquals(md5("api_keyKEYmethodauth.getTokenSECRET"), signature)
    }

    @Test
    fun `signature handles non ascii values as utf8`() {
        val signature = LastFmRequests.signature(mapOf("artist" to "Beyoncé"), secret = "s")

        assertEquals(md5("artistBeyoncés"), signature)
    }

    @Test
    fun `scrobble numbers every track and uses seconds`() {
        val params = LastFmRequests.scrobble(listOf(track, track.copy(title = "Second")))

        assertEquals("track.scrobble", params["method"])
        assertEquals("Title", params["track[0]"])
        assertEquals("Second", params["track[1]"])
        assertEquals("1700000000", params["timestamp[0]"])
        assertEquals("200", params["duration[0]"])
    }

    @Test
    fun `unknown duration and blank album are omitted`() {
        val params = LastFmRequests.nowPlaying(track.copy(album = "", durationMs = 0L))

        assertFalse(params.containsKey("album"))
        assertFalse(params.containsKey("duration"))
    }

    @Test
    fun `last fm error codes outrank the http status`() {
        assertEquals(
            SubmitResult.Unauthorized,
            LastFmRequests.resultOf(403, """{"error":9,"message":"Invalid session key"}"""),
        )
        assertEquals(
            SubmitResult.RetryLater,
            LastFmRequests.resultOf(403, """{"error":29,"message":"Rate limit exceeded"}"""),
        )
        assertEquals(
            SubmitResult.Rejected,
            LastFmRequests.resultOf(400, """{"error":6,"message":"Invalid parameters"}"""),
        )
    }

    @Test
    fun `replies without an error code fall back to the http status`() {
        assertEquals(SubmitResult.Accepted, LastFmRequests.resultOf(200, """{"scrobbles":{}}"""))
        assertEquals(SubmitResult.RetryLater, LastFmRequests.resultOf(503, "<html>"))
    }
}
