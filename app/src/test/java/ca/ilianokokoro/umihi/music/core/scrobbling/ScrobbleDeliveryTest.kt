package ca.ilianokokoro.umihi.music.core.scrobbling

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrobbleDeliveryTest {
    private class MemoryStore : PendingScrobbleStore {
        val pending = mutableMapOf<String, List<ScrobbleTrack>>()

        override suspend fun read(sinkId: String) = pending[sinkId].orEmpty()

        override suspend fun update(
            sinkId: String,
            transform: (List<ScrobbleTrack>) -> List<ScrobbleTrack>,
        ) {
            pending[sinkId] = transform(pending[sinkId].orEmpty())
        }
    }

    private class FakeSink(
        var enabled: Boolean = true,
        var respond: (List<ScrobbleTrack>) -> SubmitResult = { SubmitResult.Accepted },
    ) : ScrobbleSink {
        override val id = "fake"
        val submitted = mutableListOf<List<ScrobbleTrack>>()
        val announced = mutableListOf<ScrobbleTrack>()

        override suspend fun isEnabled() = enabled

        override suspend fun nowPlaying(track: ScrobbleTrack): SubmitResult {
            announced += track
            return SubmitResult.Accepted
        }

        override suspend fun submit(tracks: List<ScrobbleTrack>): SubmitResult {
            submitted += tracks
            return respond(tracks)
        }
    }

    private fun track(title: String = "t") =
        ScrobbleTrack(artist = "a", title = title, startedAtMs = 0L)

    @Test
    fun `accepted listen leaves nothing pending`() = runBlocking {
        val store = MemoryStore()
        val sink = FakeSink()

        ScrobbleDelivery(store, listOf(sink)).record(track())

        assertEquals(1, sink.submitted.size)
        assertTrue(store.read(sink.id).isEmpty())
    }

    @Test
    fun `offline listens wait and go out together`() = runBlocking {
        val store = MemoryStore()
        val sink = FakeSink(respond = { SubmitResult.RetryLater })
        val delivery = ScrobbleDelivery(store, listOf(sink))

        delivery.record(track("one"))
        delivery.record(track("two"))
        assertEquals(2, store.read(sink.id).size)

        sink.respond = { SubmitResult.Accepted }
        sink.submitted.clear()
        delivery.flush()

        assertEquals(listOf(listOf(track("one"), track("two"))), sink.submitted)
        assertTrue(store.read(sink.id).isEmpty())
    }

    @Test
    fun `revoked token keeps the listens`() = runBlocking {
        val store = MemoryStore()
        val sink = FakeSink(respond = { SubmitResult.Unauthorized })

        ScrobbleDelivery(store, listOf(sink)).record(track())

        assertEquals(1, store.read(sink.id).size)
    }

    @Test
    fun `one refused listen does not sink its batch`() = runBlocking {
        val store = MemoryStore()
        val sink = FakeSink(
            respond = { tracks ->
                if (tracks.any { it.title == "bad" }) SubmitResult.Rejected else SubmitResult.Accepted
            }
        )
        store.pending[sink.id] = listOf(track("good"), track("bad"), track("also good"))

        ScrobbleDelivery(store, listOf(sink)).flush()

        assertTrue(store.read(sink.id).isEmpty())
        val delivered = sink.submitted.filter { it.size == 1 && it.first().title != "bad" }
        assertEquals(2, delivered.size)
    }

    @Test
    fun `disabled service records and sends nothing`() = runBlocking {
        val store = MemoryStore()
        val sink = FakeSink(enabled = false)
        val delivery = ScrobbleDelivery(store, listOf(sink))

        delivery.record(track())
        delivery.announce(track())

        assertTrue(store.read(sink.id).isEmpty())
        assertTrue(sink.submitted.isEmpty())
        assertTrue(sink.announced.isEmpty())
    }

    @Test
    fun `large backlog is sent in batches and capped`() = runBlocking {
        val store = MemoryStore()
        val sink = FakeSink()
        store.pending[sink.id] = List(ScrobbleDelivery.MAX_PENDING) { track("$it") }

        ScrobbleDelivery(store, listOf(sink)).record(track("newest"))

        assertTrue(sink.submitted.all { it.size <= ScrobbleDelivery.BATCH_SIZE })
        assertEquals(ScrobbleDelivery.MAX_PENDING, sink.submitted.sumOf { it.size })
        assertEquals("newest", sink.submitted.last().last().title)
    }
}
