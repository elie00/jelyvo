package ca.ilianokokoro.umihi.music.desktop.playback

import ca.ilianokokoro.umihi.music.core.domain.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class JavaFxAudioEngineTest {
    private class Output : AudioOutput {
        var events: AudioOutput.Events? = null
        val urls = mutableListOf<String>()
        var plays = 0
        var closes = 0
        var position = -1L
        override fun dispatch(action: () -> Unit) = action()
        override fun open(url: String, events: AudioOutput.Events) {
            urls += url
            this.events = events
            events.ready()
        }
        override fun play() { plays++; events?.playing(true) }
        override fun pause() { events?.playing(false) }
        override fun seek(positionMs: Long) { position = positionMs }
        override fun volume(value: Double) = Unit
        override fun close() { closes++ }
    }

    private fun engine(output: Output, resolve: suspend (Track) -> String = { it.id }) =
        JavaFxAudioEngine(object : StreamResolver {
            override suspend fun resolve(track: Track, allowLocal: Boolean, kind: StreamKind) = ResolvedStream(resolve(track))
            override suspend fun resolveRemote(track: Track) = ResolvedRemoteStream(track.id, "audio/mp4")
        }, CoroutineScope(Dispatchers.Unconfined), output, Random(7))

    private fun queue(vararg ids: String) = PlaybackQueue(ids.map { QueueEntry(it, Track(it)) })

    @Test fun `empty load retires playback and clears progress`() = runBlocking {
        val output = Output()
        val engine = engine(output)
        engine.load(queue("a"), 0)
        output.events!!.progress(PlaybackProgress(500, 1000))
        val old = output.events!!
        engine.load(queue(), 0)
        old.playing(true)
        old.progress(PlaybackProgress(900, 1000))
        assertNull(engine.state.value.currentTrack)
        assertFalse(engine.state.value.isPlaying)
        assertNull(engine.progress())
        engine.release()
    }

    @Test fun `replaced resolution cannot open or fail newer track`() = runBlocking {
        val gate = CompletableDeferred<String>()
        val output = Output()
        val engine = engine(output) { track ->
            if (track.id == "old") withContext(NonCancellable) { gate.await(); error("stale") }
            else track.id
        }
        engine.load(queue("old", "new"), 0)
        engine.skipNext()
        gate.complete("old")
        assertEquals(listOf("new"), output.urls)
        assertEquals("new", engine.state.value.currentTrack?.id)
        assertNull(engine.error.value)
        assertTrue(engine.state.value.isPlaying)
        engine.release()
    }

    @Test fun `pause during resolution prevents autoplay`() = runBlocking {
        val gate = CompletableDeferred<String>()
        val output = Output()
        val engine = engine(output) { gate.await() }
        engine.load(queue("a"), 0)
        engine.pause()
        gate.complete("a")
        assertEquals(listOf("a"), output.urls)
        assertEquals(0, output.plays)
        assertFalse(engine.state.value.isPlaying)
        engine.play()
        assertEquals(1, output.plays)
        engine.release()
    }

    @Test fun `shuffle visits every slot once including duplicate tracks and previous follows order`() = runBlocking {
        val output = Output()
        val engine = engine(output)
        engine.load(queue("a", "b", "a", "c", "d", "e"), 0)
        engine.setShuffleEnabled(true)
        val visited = mutableListOf(engine.state.value.currentIndex)
        repeat(5) { engine.skipNext(); visited += engine.state.value.currentIndex }
        assertEquals(6, visited.toSet().size)
        assertNotEquals((0..5).toList(), visited)
        engine.skipPrevious()
        assertEquals(visited[4], engine.state.value.currentIndex)
        engine.release()
    }

    @Test fun `repeat one is automatic and repeat all wraps explicit next`() = runBlocking {
        val output = Output()
        val engine = engine(output)
        engine.load(queue("a", "b"), 0)
        engine.setRepeatMode(RepeatMode.ONE)
        output.events!!.ended()
        assertEquals(listOf("a", "a"), output.urls)
        engine.skipNext()
        assertEquals("b", engine.state.value.currentTrack?.id)
        engine.setRepeatMode(RepeatMode.ALL)
        engine.skipNext()
        assertEquals("a", engine.state.value.currentTrack?.id)
        engine.release()
    }

    @Test fun `remove and reorder preserve active queue slot`() = runBlocking {
        val output = Output()
        val engine = engine(output)
        engine.load(queue("a", "a", "b"), 1)
        val active = engine.queue.value.current!!.entryId
        engine.move(1, 2)
        assertEquals(active, engine.queue.value.current!!.entryId)
        assertEquals(2, engine.state.value.currentIndex)
        engine.remove(0)
        assertEquals(active, engine.queue.value.current!!.entryId)
        assertEquals(1, engine.state.value.currentIndex)
        engine.remove(1)
        assertEquals("b", engine.state.value.currentTrack?.id)
        engine.release()
    }

    @Test fun `release cancels pending work and rejects retired callbacks`() = runBlocking {
        val output = Output()
        val engine = engine(output)
        engine.load(queue("a", "b"), 0)
        val old = output.events!!
        engine.release()
        old.ended()
        engine.skipNext()
        assertEquals(listOf("a"), output.urls)
        assertEquals(PlaybackState(), engine.state.value)
    }

    @Test fun `failure is visible and play retries current track`() = runBlocking {
        val output = Output()
        var fail = true
        val engine = engine(output) { if (fail) error("signed URL must not reach UI") else it.id }
        engine.load(queue("a"), 0)
        assertNotNull(engine.error.value)
        assertFalse(engine.error.value!!.contains("signed URL"))
        fail = false
        engine.play()
        assertNull(engine.error.value)
        assertTrue(engine.state.value.isPlaying)
        engine.release()
    }
}
