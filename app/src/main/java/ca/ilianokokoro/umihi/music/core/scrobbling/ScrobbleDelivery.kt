package ca.ilianokokoro.umihi.music.core.scrobbling

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A scrobbling service. Not enabled means the user has not connected it. */
interface ScrobbleSink {
    val id: String

    suspend fun isEnabled(): Boolean

    suspend fun nowPlaying(track: ScrobbleTrack): SubmitResult

    suspend fun submit(tracks: List<ScrobbleTrack>): SubmitResult
}

/** Listens still waiting to reach a service, kept per service so one outage cannot hold another. */
interface PendingScrobbleStore {
    suspend fun read(sinkId: String): List<ScrobbleTrack>

    suspend fun update(sinkId: String, transform: (List<ScrobbleTrack>) -> List<ScrobbleTrack>)
}

/**
 * Every listen is written down before it is sent, so a listen made offline, or lost to a dying
 * process mid-request, is delivered by a later flush instead of disappearing.
 */
class ScrobbleDelivery(
    private val store: PendingScrobbleStore,
    private val sinks: List<ScrobbleSink>,
) {
    private val flushLock = Mutex()

    /** Best effort and never queued: a late "now playing" would be wrong. */
    suspend fun announce(track: ScrobbleTrack) {
        sinks.forEach { sink -> if (sink.isEnabled()) sink.nowPlaying(track) }
    }

    suspend fun record(track: ScrobbleTrack) {
        sinks.forEach { sink ->
            if (sink.isEnabled()) {
                store.update(sink.id) { pending -> (pending + track).takeLast(MAX_PENDING) }
            }
        }
        flush()
    }

    suspend fun flush() = flushLock.withLock {
        sinks.forEach { sink -> if (sink.isEnabled()) flush(sink) }
    }

    private suspend fun flush(sink: ScrobbleSink) {
        var batchSize = BATCH_SIZE
        while (true) {
            val batch = store.read(sink.id).take(batchSize)
            if (batch.isEmpty()) return
            when (sink.submit(batch)) {
                SubmitResult.Accepted -> store.update(sink.id) { it.drop(batch.size) }

                // A refusal does not say which listen was at fault: isolate it before dropping,
                // otherwise one bad track would take the rest of its batch with it.
                SubmitResult.Rejected ->
                    if (batch.size > 1) {
                        batchSize = 1
                    } else {
                        store.update(sink.id) { it.drop(1) }
                    }

                SubmitResult.RetryLater, SubmitResult.Unauthorized -> return
            }
        }
    }

    companion object {
        const val BATCH_SIZE = 50

        // Pending listens live in DataStore, which rewrites its whole file on every edit.
        const val MAX_PENDING = 500
    }
}
