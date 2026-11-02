package ca.ilianokokoro.umihi.music.core.jam

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JamProtocolTest {
    private val track = PartyTrack(videoId = "abc", title = "Song", durationMs = 200_000L)
    private val playing = PartyPlayback(
        seq = 1,
        track = track,
        isPlaying = true,
        positionMs = 40_000L,
        anchorMs = 1_000_000L,
    )

    @Test
    fun `playhead advances from its anchor only while playing`() {
        assertEquals(42_000L, playing.positionAt(1_002_000L))
        assertEquals(40_000L, playing.copy(isPlaying = false).positionAt(1_002_000L))
    }

    @Test
    fun `playhead holds before a scheduled start`() {
        assertEquals(40_000L, playing.positionAt(999_650L))
    }

    @Test
    fun `clock offset comes from the fastest round trip`() {
        val clock = ServerClock { 10_000L }
        clock.record(sentAtLocalMs = 1_000L, serverMs = 6_000L, receivedAtLocalMs = 1_600L)
        assertEquals(4_700L, clock.offsetMs)

        clock.record(sentAtLocalMs = 2_000L, serverMs = 7_020L, receivedAtLocalMs = 2_040L)
        assertEquals(5_000L, clock.offsetMs)

        clock.record(sentAtLocalMs = 3_000L, serverMs = 9_000L, receivedAtLocalMs = 3_900L)
        assertEquals(5_000L, clock.offsetMs)
        assertEquals(15_000L, clock.serverNowMs())
    }

    @Test
    fun `no server time before the first sample`() {
        assertNull(ServerClock { 0L }.serverNowMs())
    }

    @Test
    fun `old samples stop counting`() {
        val clock = ServerClock { 0L }
        clock.record(0L, 5_000L, 0L)
        clock.record(200_000L, 200_500L + 7_000L, 201_000L)

        assertEquals(7_000L, clock.offsetMs)
    }

    @Test
    fun `in sync means nothing to do`() {
        val local = LocalPlayback("abc", positionMs = 40_300L, isPlaying = true)

        assertTrue(JamSync.plan(playing, 1_000_000L, local).isEmpty())
    }

    @Test
    fun `drift beyond tolerance seeks to the party`() {
        val local = LocalPlayback("abc", positionMs = 30_000L, isPlaying = true)

        assertEquals(
            listOf(JamAction.Seek(41_000L)),
            JamSync.plan(playing, 1_001_000L, local),
        )
    }

    @Test
    fun `different track changes track at the party position`() {
        val local = LocalPlayback("other", positionMs = 0L, isPlaying = false)

        assertEquals(
            listOf(JamAction.ChangeTrack(track, 41_000L, play = true)),
            JamSync.plan(playing, 1_001_000L, local),
        )
    }

    @Test
    fun `pause follows the party`() {
        val paused = playing.copy(isPlaying = false)
        val local = LocalPlayback("abc", positionMs = 40_100L, isPlaying = true)

        assertEquals(
            listOf(JamAction.SetPlaying(false)),
            JamSync.plan(paused, 1_002_000L, local),
        )
    }

    @Test
    fun `target never passes the end of the track`() {
        val local = LocalPlayback("other", positionMs = 0L, isPlaying = true)
        val action = JamSync.plan(playing, 9_000_000L, local).single() as JamAction.ChangeTrack

        assertEquals(200_000L, action.positionMs)
    }

    @Test
    fun `an empty party asks nothing of a device`() {
        val local = LocalPlayback("abc", positionMs = 0L, isPlaying = true)

        assertTrue(JamSync.plan(PartyPlayback(), 0L, local).isEmpty())
    }

    @Test
    fun `a host opens an empty party with what it is playing`() {
        val empty = PartyPlayback()

        assertTrue(JamSync.shouldPublishCurrentTrack(empty, isHost = true, mediaId = "abc"))
    }

    @Test
    fun `only the host opens an empty party`() {
        assertFalse(JamSync.shouldPublishCurrentTrack(PartyPlayback(), isHost = false, mediaId = "abc"))
    }

    @Test
    fun `a host with nothing loaded has nothing to publish`() {
        assertFalse(JamSync.shouldPublishCurrentTrack(PartyPlayback(), isHost = true, mediaId = null))
    }

    @Test
    fun `a party that already has a track is left alone`() {
        assertFalse(JamSync.shouldPublishCurrentTrack(playing, isHost = true, mediaId = "other"))
    }

    @Test
    fun `an applied state is not mistaken for a local seek`() {
        assertFalse(JamSync.isLocalSeek(playing, 1_000_000L, 40_200L))
        assertTrue(JamSync.isLocalSeek(playing, 1_000_000L, 90_000L))
    }

    @Test
    fun `server url must be https`() {
        assertEquals("https://jam.example", normalizeJamServerUrl(" https://jam.example/ "))
        assertNull(normalizeJamServerUrl("http://jam.example"))
        assertNull(normalizeJamServerUrl("jam.example"))
        assertNull(normalizeJamServerUrl("https://"))
    }

    @Test
    fun `the default server is one the app accepts`() {
        assertEquals(DEFAULT_JAM_SERVER_URL, normalizeJamServerUrl(DEFAULT_JAM_SERVER_URL))
    }

    @Test
    fun `frames decode despite fields this client ignores`() {
        val json = Json { ignoreUnknownKeys = true }
        val frame = json.decodeFromString<ServerFrame>(
            """{"type":"state","serverMs":5,"playback":{"seq":3,"isPlaying":true,
                "positionMs":1,"anchorMs":2,"queueSeq":9,"track":{"videoId":"x","fromAutoplay":false}}}"""
        )

        assertEquals("state", frame.type)
        assertEquals(3L, frame.playback?.seq)
        assertEquals("x", frame.playback?.track?.videoId)
    }
}
