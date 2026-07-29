package ca.ilianokokoro.umihi.music.core.reco

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class MixGenerationInputsProviderTest {

    private val snapshot = MixGenerationInputs(
        profile = TasteProfile(computedAtMs = 1),
        candidates = emptyList(),
    )

    @Test
    fun `concurrent callers share one in-flight computation`() = runBlocking {
        val computationStarted = CompletableDeferred<Unit>()
        val allowCompletion = CompletableDeferred<Unit>()
        var calls = 0
        val requestedTimes = mutableListOf<Long>()
        val provider = MixGenerationInputsProvider { nowMs ->
            calls += 1
            requestedTimes += nowMs
            computationStarted.complete(Unit)
            allowCompletion.await()
            snapshot
        }

        val daily = async { provider.load(nowMs = 100) }
        computationStarted.await()
        val context = async { provider.load(nowMs = 200) }
        yield()
        allowCompletion.complete(Unit)

        assertSame(snapshot, daily.await())
        assertSame(snapshot, context.await())
        assertEquals(1, calls)
        assertEquals(listOf(100L), requestedTimes)
    }

    @Test
    fun `a later generation receives a fresh snapshot`() = runBlocking {
        var calls = 0
        val provider = MixGenerationInputsProvider { nowMs ->
            calls += 1
            MixGenerationInputs(
                profile = TasteProfile(computedAtMs = nowMs),
                candidates = emptyList(),
            )
        }

        assertEquals(100L, provider.load(100)?.profile?.computedAtMs)
        assertEquals(200L, provider.load(200)?.profile?.computedAtMs)
        assertEquals(2, calls)
    }
}
