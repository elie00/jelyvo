package ca.ilianokokoro.umihi.music.data.repositories

import android.content.Context
import ca.ilianokokoro.umihi.music.data.database.AppDatabase
import ca.ilianokokoro.umihi.music.models.PlayEvent
import ca.ilianokokoro.umihi.music.models.PlayEventAggregate

/**
 * Persistence for the raw listening signals ([PlayEvent]) collected by the playback service.
 * Read side is deliberately suspend-based (not Flow): the recommendation passes consume a
 * snapshot of the window, they do not observe it live.
 */
class PlayEventRepository(context: Context) {
    private val localPlayEventDataSource = AppDatabase.getInstance(context).playEventRepository()

    suspend fun record(event: PlayEvent) {
        if (event.youtubeId.isBlank()) return
        localPlayEventDataSource.insert(event)
    }

    suspend fun eventsSince(epochMs: Long): List<PlayEvent> =
        localPlayEventDataSource.eventsSince(epochMs)

    suspend fun eventsForSong(youtubeId: String): List<PlayEvent> =
        localPlayEventDataSource.eventsForSong(youtubeId)

    suspend fun aggregatesSince(epochMs: Long): List<PlayEventAggregate> =
        localPlayEventDataSource.aggregatesSince(epochMs)

    /** Drops events past the retention window; [nowMs] injected so callers stay testable. */
    suspend fun purgeExpired(nowMs: Long): Int =
        localPlayEventDataSource.deleteOlderThan(nowMs - RETENTION_MS)

    companion object {
        const val RETENTION_DAYS = 365
        const val RETENTION_MS = RETENTION_DAYS * 24L * 60 * 60 * 1000
    }
}
