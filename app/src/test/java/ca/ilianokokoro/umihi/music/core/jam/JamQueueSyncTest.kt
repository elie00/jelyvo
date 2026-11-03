package ca.ilianokokoro.umihi.music.core.jam

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JamQueueSyncTest {
    private fun track(id: String) = PartyTrack(videoId = id, title = id)
    private fun tracks(vararg ids: String) = ids.map(::track)
    private fun numbered(count: Int) = List(count) { track("t$it") }
    private fun ids(window: QueueWindow) = window.tracks.map(PartyTrack::videoId)

    @Test
    fun `a short queue is shared whole`() {
        val window = JamQueueSync.window(tracks("a", "b", "c"), 1) { 100 }
        assertEquals(listOf("a", "b", "c"), ids(window))
        assertEquals(1, window.index)
    }

    @Test
    fun `a long queue is cut around the playing track`() {
        val window = JamQueueSync.window(numbered(100), 50) { 100 }
        assertEquals(JamQueueSync.HISTORY_TRACKS + 1 + JamQueueSync.UPCOMING_TRACKS, window.tracks.size)
        assertEquals("t50", window.tracks[window.index].videoId)
        assertEquals("t75", window.tracks.last().videoId)
    }

    @Test
    fun `history goes before anything still to come`() {
        val perTrack = JamQueueSync.MAX_TRACK_BYTES / 27
        val window = JamQueueSync.window(numbered(100), 50) { perTrack }
        assertEquals("t75", window.tracks.last().videoId)
        assertEquals(1, window.index)
    }

    @Test
    fun `tracks that do not fit are dropped, but never the playing one`() {
        val window = JamQueueSync.window(numbered(10), 3) { JamQueueSync.MAX_TRACK_BYTES * 2 }
        assertEquals(listOf("t3"), ids(window))
        assertEquals(0, window.index)
    }

    @Test
    fun `the largest window a real queue makes fits the server's limit`() {
        val json = Json
        val long = "x".repeat(300)
        val queue = List(40) {
            PartyTrack(
                videoId = "video$it",
                title = long,
                artist = long,
                thumbnailUrl = "https://example.com/${"y".repeat(200)}",
                durationMs = 200_000,
            )
        }
        val window = JamQueueSync.window(queue, 10) { json.encodeToString(it).length }
        val encoded = json.encodeToString(window.tracks)
        assertTrue("was ${encoded.length}", encoded.length + 200 < 16_384)
        assertEquals(10L, queue.indexOf(window.tracks[window.index]).toLong())
    }

    @Test
    fun `nothing playing means nothing to share`() {
        assertEquals(QueueWindow(emptyList(), -1), JamQueueSync.window(emptyList(), -1) { 1 })
        assertEquals(QueueWindow(emptyList(), -1), JamQueueSync.window(tracks("a"), 3) { 1 })
    }

    @Test
    fun `an identical queue is left alone`() {
        val change = JamQueueSync.plan(
            localIds = listOf("a", "b"),
            currentId = "a",
            remote = PartyQueue(seq = 1, index = 0, items = tracks("a", "b")),
        )
        assertEquals(QueueChange.None, change)
    }

    @Test
    fun `an empty party queue never empties this device`() {
        val change = JamQueueSync.plan(listOf("a"), "a", PartyQueue(seq = 1, index = -1))
        assertEquals(QueueChange.None, change)
    }

    @Test
    fun `what is playing stays, and the rest changes around it`() {
        val change = JamQueueSync.plan(
            localIds = listOf("old", "b", "old2"),
            currentId = "b",
            remote = PartyQueue(seq = 1, index = 1, items = tracks("x", "b", "y", "z")),
        )
        assertEquals(QueueChange.AroundCurrent(tracks("x"), tracks("y", "z")), change)
    }

    @Test
    fun `a repeated track is anchored at the party's position`() {
        val change = JamQueueSync.plan(
            localIds = listOf("x"),
            currentId = "x",
            remote = PartyQueue(seq = 1, index = 2, items = tracks("x", "a", "x", "b")),
        )
        assertEquals(QueueChange.AroundCurrent(tracks("x", "a"), tracks("b")), change)
    }

    @Test
    fun `a playing track the party does not have means replacing everything`() {
        val change = JamQueueSync.plan(
            localIds = listOf("mine"),
            currentId = "mine",
            remote = PartyQueue(seq = 1, index = 1, items = tracks("a", "b")),
        )
        assertEquals(QueueChange.Replace(tracks("a", "b"), 1), change)
    }

    @Test
    fun `nothing playing here takes the party's queue`() {
        val change = JamQueueSync.plan(
            localIds = emptyList(),
            currentId = null,
            remote = PartyQueue(seq = 1, index = 0, items = tracks("a")),
        )
        assertEquals(QueueChange.Replace(tracks("a"), 0), change)
    }

    @Test
    fun `an index the server left unset still lands in the queue`() {
        val change = JamQueueSync.plan(
            localIds = emptyList(),
            currentId = null,
            remote = PartyQueue(seq = 1, index = -1, items = tracks("a", "b")),
        )
        assertEquals(QueueChange.Replace(tracks("a", "b"), 0), change)
    }

    @Test
    fun `finds the nearest copy, the next one on a tie`() {
        val ids = listOf("x", "a", "x", "b", "x")
        assertEquals(2, JamQueueSync.nearestIndexOf(ids, "x", 2))
        assertEquals(4, JamQueueSync.nearestIndexOf(ids, "x", 3))
        assertEquals(-1, JamQueueSync.nearestIndexOf(ids, "missing", 0))
    }

    @Test
    fun `tops up only when the shared queue runs short and this device has more`() {
        val shared = listOf("a", "b", "c")
        assertTrue(JamQueueSync.needsTopUp(shared, "a", localUpcoming = 20))
        assertFalse(JamQueueSync.needsTopUp(shared, "a", localUpcoming = 2))
        assertFalse(JamQueueSync.needsTopUp(shared, "a", localUpcoming = 1))
        assertFalse(JamQueueSync.needsTopUp(numbered(10).map { it.videoId }, "t0", localUpcoming = 20))
        assertFalse(JamQueueSync.needsTopUp(shared, "elsewhere", localUpcoming = 20))
    }

    @Test
    fun `a queue frame is read as the server sends it`() {
        val frame = Json { ignoreUnknownKeys = true }.decodeFromString<ServerFrame>(
            """{"type":"queue","queue":{"seq":3,"index":1,"items":[
                {"videoId":"a","title":"A","artist":"","fromAutoplay":false},
                {"videoId":"b","title":"B","artist":"","durationMs":1000,"fromAutoplay":true}
            ]},"serverMs":5}"""
        )
        assertEquals(3L, frame.queue?.seq)
        assertEquals(1, frame.queue?.index)
        assertEquals(listOf("a", "b"), frame.queue?.items?.map(PartyTrack::videoId))
    }

    @Test
    fun `a state frame carries the queue's sequence`() {
        val frame = Json { ignoreUnknownKeys = true }.decodeFromString<ServerFrame>(
            """{"type":"state","playback":{"seq":2,"queueSeq":7,"queueIndex":1}}"""
        )
        assertEquals(7L, frame.playback?.queueSeq)
    }

    @Test
    fun `a shared duration reads the way song rows write it`() {
        assertEquals("3:20", JamQueueSync.durationLabel(200_000L))
        assertEquals("1:02:05", JamQueueSync.durationLabel(3_725_000L))
    }

    @Test
    fun `a track without a duration shows none`() {
        assertEquals("", JamQueueSync.durationLabel(null))
        assertEquals("", JamQueueSync.durationLabel(0L))
    }

    @Test
    fun `a song row's duration survives the trip through a party`() {
        val label = "4:30"
        assertEquals(label, JamQueueSync.durationLabel(JamQueueSync.durationMillis(label)))
        assertEquals(null, JamQueueSync.durationMillis(""))
        assertEquals(null, JamQueueSync.durationMillis(null))
    }

    @Test
    fun `tracks beyond what was shared stay in the queue`() {
        val change = JamQueueSync.plan(
            localIds = listOf("a", "b", "c", "d", "e"),
            currentId = "a",
            remote = PartyQueue(seq = 2, index = 0, items = tracks("a", "c", "b")),
            sharedIds = listOf("a", "b", "c"),
        )

        assertEquals(QueueChange.AroundCurrent(emptyList(), tracks("c", "b"), keptTail = 2), change)
    }

    @Test
    fun `a device holding only what was shared keeps nothing else`() {
        val change = JamQueueSync.plan(
            localIds = listOf("a", "b", "c"),
            currentId = "a",
            remote = PartyQueue(seq = 2, index = 0, items = tracks("a", "c", "b")),
            sharedIds = listOf("a", "b", "c"),
        )

        assertEquals(QueueChange.AroundCurrent(emptyList(), tracks("c", "b"), keptTail = 0), change)
    }

    @Test
    fun `nothing is kept when nothing was shared before`() {
        val change = JamQueueSync.plan(
            localIds = listOf("a", "b", "c"),
            currentId = "a",
            remote = PartyQueue(seq = 2, index = 0, items = tracks("a", "c")),
        )

        assertEquals(QueueChange.AroundCurrent(emptyList(), tracks("c"), keptTail = 0), change)
    }
}
