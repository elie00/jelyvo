package ca.ilianokokoro.umihi.music.core.scrobbling

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenBrainzPayloadTest {
    private val track = ScrobbleTrack(
        artist = "Daft \"Punk\"\nDuo",
        title = "One More Time",
        album = "Discovery",
        durationMs = 320_000L,
        startedAtMs = 1_700_000_000_123L,
    )

    private fun JsonObject.firstListen() = getValue("payload").jsonArray.first().jsonObject

    private fun JsonObject.metadata() = firstListen().getValue("track_metadata").jsonObject

    @Test
    fun `playing now carries no timestamp`() {
        val body = ListenBrainzPayload.playingNow(track)

        assertEquals("playing_now", body.getValue("listen_type").jsonPrimitive.content)
        assertFalse(body.firstListen().containsKey("listened_at"))
    }

    @Test
    fun `one listen is single and timestamped in seconds`() {
        val body = ListenBrainzPayload.listens(listOf(track))

        assertEquals("single", body.getValue("listen_type").jsonPrimitive.content)
        assertEquals(1_700_000_000L, body.firstListen().getValue("listened_at").jsonPrimitive.long)
    }

    @Test
    fun `a backlog goes as an import`() {
        val body = ListenBrainzPayload.listens(listOf(track, track))

        assertEquals("import", body.getValue("listen_type").jsonPrimitive.content)
        assertEquals(2, body.getValue("payload").jsonArray.size)
    }

    @Test
    fun `quotes and newlines survive encoding`() {
        val metadata = ListenBrainzPayload.listens(listOf(track)).metadata()

        assertEquals(track.artist, metadata.getValue("artist_name").jsonPrimitive.content)
    }

    @Test
    fun `unknown duration and blank album are omitted`() {
        val bare = track.copy(album = " ", durationMs = 0L)
        val metadata = ListenBrainzPayload.listens(listOf(bare)).metadata()

        assertFalse(metadata.containsKey("release_name"))
        assertFalse(metadata.getValue("additional_info").jsonObject.containsKey("duration_ms"))
    }

    @Test
    fun `known duration is sent`() {
        val metadata = ListenBrainzPayload.listens(listOf(track)).metadata()
        val info = metadata.getValue("additional_info").jsonObject

        assertTrue(info.containsKey("duration_ms"))
        assertEquals("Jelyvo", info.getValue("submission_client").jsonPrimitive.content)
    }

    @Test
    fun `token reply is read from its valid flag`() {
        assertTrue(isValidTokenResponse("""{"code":200,"valid":true,"user_name":"x"}"""))
        assertFalse(isValidTokenResponse("""{"code":200,"valid":false}"""))
        assertFalse(isValidTokenResponse("not json"))
    }

    @Test
    fun `http status decides the fate of a submission`() {
        assertEquals(SubmitResult.Accepted, submitResultOf(200))
        assertEquals(SubmitResult.Unauthorized, submitResultOf(401))
        assertEquals(SubmitResult.RetryLater, submitResultOf(429))
        assertEquals(SubmitResult.RetryLater, submitResultOf(503))
        assertEquals(SubmitResult.Rejected, submitResultOf(400))
    }
}
