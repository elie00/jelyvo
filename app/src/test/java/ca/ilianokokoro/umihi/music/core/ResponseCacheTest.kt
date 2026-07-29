package ca.ilianokokoro.umihi.music.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ResponseCacheTest {

    @Test
    fun `concurrent callers share one fetch for the same key`() = runBlocking {
        val fetchCount = AtomicInteger()
        val fetchStarted = CompletableDeferred<Unit>()
        val releaseFetch = CompletableDeferred<Unit>()

        val first = async {
            ResponseCache.getOrFetch("single-flight") {
                fetchCount.incrementAndGet()
                fetchStarted.complete(Unit)
                releaseFetch.await()
                "fresh"
            }
        }
        fetchStarted.await()
        val second = async {
            ResponseCache.getOrFetch("single-flight") {
                fetchCount.incrementAndGet()
                "duplicate"
            }
        }
        yield()
        releaseFetch.complete(Unit)

        assertEquals("fresh", first.await())
        assertEquals("fresh", second.await())
        assertEquals(1, fetchCount.get())
    }

    @Test
    fun `completed fetch releases the key for a later refresh`() = runBlocking {
        val fetchCount = AtomicInteger()

        val first = ResponseCache.getOrFetch("refreshable") {
            "value-${fetchCount.incrementAndGet()}"
        }
        val second = ResponseCache.getOrFetch("refreshable") {
            "value-${fetchCount.incrementAndGet()}"
        }

        assertEquals("value-1", first)
        assertEquals("value-2", second)
        assertEquals(2, fetchCount.get())
    }
}
