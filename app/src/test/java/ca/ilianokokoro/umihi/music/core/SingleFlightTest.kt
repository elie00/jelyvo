package ca.ilianokokoro.umihi.music.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SingleFlightTest {

    @Test
    fun `overlapping calls share one execution`() = runBlocking {
        val singleFlight = SingleFlight()
        val executions = AtomicInteger()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        val first = async {
            singleFlight.execute("track") {
                executions.incrementAndGet()
                started.complete(Unit)
                release.await()
                "stream"
            }
        }
        started.await()
        val second = async {
            singleFlight.execute("track") {
                executions.incrementAndGet()
                "duplicate"
            }
        }
        yield()
        release.complete(Unit)

        assertEquals("stream", first.await())
        assertEquals("stream", second.await())
        assertEquals(1, executions.get())
    }

    @Test
    fun `failure is shared and a later call can retry`() = runBlocking {
        val singleFlight = SingleFlight()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        supervisorScope {
            val first = async {
                singleFlight.execute("track") {
                    started.complete(Unit)
                    release.await()
                    error("offline")
                }
            }
            started.await()
            val second = async {
                singleFlight.execute("track") { "must not run" }
            }
            yield()
            release.complete(Unit)

            assertTrue(runCatching { first.await() }.exceptionOrNull() is IllegalStateException)
            assertTrue(runCatching { second.await() }.exceptionOrNull() is IllegalStateException)
        }
        assertEquals("recovered", singleFlight.execute("track") { "recovered" })
    }
}
