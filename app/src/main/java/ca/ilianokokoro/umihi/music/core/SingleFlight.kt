package ca.ilianokokoro.umihi.music.core

import kotlinx.coroutines.CompletableDeferred

/**
 * Coalesces concurrent work by key. The first caller owns the computation; every overlapping
 * caller awaits the same result. Completed and failed keys are removed so a later call can retry.
 *
 * This is a process-local module: it deliberately knows nothing about caching or persistence.
 */
class SingleFlight {
    private val inFlight = HashMap<String, CompletableDeferred<Any>>()

    @Suppress("UNCHECKED_CAST")
    suspend fun <T : Any> execute(key: String, block: suspend () -> T): T {
        val (deferred, ownsExecution) = synchronized(inFlight) {
            inFlight[key]?.let { existing -> existing to false }
                ?: (CompletableDeferred<Any>().also { created ->
                    inFlight[key] = created
                } to true)
        }

        if (ownsExecution) {
            try {
                deferred.complete(block())
            } catch (throwable: Throwable) {
                deferred.completeExceptionally(throwable)
            } finally {
                synchronized(inFlight) {
                    if (inFlight[key] === deferred) inFlight.remove(key)
                }
            }
        }

        return deferred.await() as T
    }
}
