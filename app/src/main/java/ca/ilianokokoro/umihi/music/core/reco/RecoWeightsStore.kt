package ca.ilianokokoro.umihi.music.core.reco

import android.content.Context
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printe
import ca.ilianokokoro.umihi.music.data.repositories.HistoryRepository
import ca.ilianokokoro.umihi.music.data.repositories.PlayEventRepository
import ca.ilianokokoro.umihi.music.models.PlayEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.ZoneId

/** Inputs the tuner reads. Internal so tests can replace the Room-backed repositories. */
internal interface RecoWeightsInputs {
    suspend fun eventsSince(epochMs: Long): List<PlayEvent>
    suspend fun artistByYoutubeId(): Map<String, String>
}

/** Serialized snapshot storage. Production uses filesDir; tests use an in-memory adapter. */
internal interface RecoWeightsSnapshotStore {
    fun read(): String?
    fun write(value: String)
    fun delete()
}

private class RepositoryRecoWeightsInputs(context: Context) : RecoWeightsInputs {
    private val playEventRepository = PlayEventRepository(context.applicationContext)
    private val historyRepository = HistoryRepository(context.applicationContext)

    override suspend fun eventsSince(epochMs: Long) =
        playEventRepository.eventsSince(epochMs)

    override suspend fun artistByYoutubeId() =
        historyRepository.artistByYoutubeId()
}

private class FileRecoWeightsSnapshotStore(context: Context) : RecoWeightsSnapshotStore {
    private val file = File(context.applicationContext.filesDir, RecoWeightsStore.WEIGHTS_FILE_NAME)

    override fun read(): String? = file.takeIf(File::exists)?.readText()
    override fun write(value: String) = file.writeText(value)
    override fun delete() {
        file.delete()
    }
}

/** The two tuned weight sets plus the tuning bookkeeping, persisted as one JSON file. */
@Serializable
data class StoredRecoWeights(
    val daily: RecoWeights = RecoWeights.DAILY,
    val context: RecoWeights = RecoWeights.CONTEXT,
    /** Local epoch day ([dailyMixEpochDay]) of the last tuning pass; -1 = never tuned. */
    val lastTunedEpochDay: Long = -1,
    val updatedAtMs: Long = 0,
)

/**
 * Owns the tuned [RecoWeights] the mix pipelines rank with: loads/persists them as a JSON file
 * in `filesDir`, runs the [tuneWeights] feedback pass at most once per local day, and exposes
 * the current state as a [StateFlow] for the debug screen. Failures never propagate — the
 * defaults are always a safe answer.
 */
class RecoWeightsStore internal constructor(
    private val inputs: RecoWeightsInputs,
    private val snapshotStore: RecoWeightsSnapshotStore,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    private constructor(context: Context) : this(
        inputs = RepositoryRecoWeightsInputs(context),
        snapshotStore = FileRecoWeightsSnapshotStore(context),
    )

    private val _weights = MutableStateFlow<StoredRecoWeights?>(null)

    /** Last loaded/persisted state; null until the first [current] call touches the disk. */
    val weights: StateFlow<StoredRecoWeights?> = _weights.asStateFlow()

    /** Returns the persisted weights, loading them from disk on first call. */
    suspend fun current(): StoredRecoWeights = mutex.withLock { loadLocked() }

    /**
     * Runs one feedback pass ([tuneWeights]) per local day: the daily weights learn from the
     * "daily-mix" plays, the context weights from the "context-mix" plays. Called by the mix
     * repositories right before generating, so tuning needs no worker of its own.
     */
    suspend fun maybeTune(
        profile: TasteProfile,
        nowMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        try {
            mutex.withLock {
                val stored = loadLocked()
                val today = dailyMixEpochDay(nowMs, zone)
                if (stored.lastTunedEpochDay == today) return@withLock

                val events = inputs.eventsSince(nowMs - TUNER_WINDOW_MS)
                val artistByYoutubeId = inputs.artistByYoutubeId()
                persistLocked(
                    stored.copy(
                        daily = tuneWeights(
                            current = stored.daily,
                            events = events,
                            profile = profile,
                            artistByYoutubeId = artistByYoutubeId,
                            sourceContext = DAILY_MIX_SOURCE_CONTEXT,
                            nowMs = nowMs,
                            defaults = RecoWeights.DAILY,
                        ),
                        context = tuneWeights(
                            current = stored.context,
                            events = events,
                            profile = profile,
                            artistByYoutubeId = artistByYoutubeId,
                            sourceContext = CONTEXT_MIX_SOURCE_CONTEXT,
                            nowMs = nowMs,
                            defaults = RecoWeights.CONTEXT,
                        ),
                        lastTunedEpochDay = today,
                        updatedAtMs = nowMs,
                    )
                )
            }
        } catch (exception: Exception) {
            printe(message = "Failed to tune the reco weights", exception = exception)
        }
    }

    /** Debug-screen escape hatch: back to the defaults, holding until tomorrow's pass. */
    suspend fun reset(
        nowMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        mutex.withLock {
            persistLocked(
                StoredRecoWeights(
                    lastTunedEpochDay = dailyMixEpochDay(nowMs, zone),
                    updatedAtMs = nowMs,
                )
            )
        }
    }

    private fun loadLocked(): StoredRecoWeights {
        _weights.value?.let { return it }
        val loaded = try {
            val persisted = snapshotStore.read()
            if (persisted == null) {
                StoredRecoWeights()
            } else {
                json.decodeFromString(StoredRecoWeights.serializer(), persisted)
            }
        } catch (exception: Exception) {
            // A corrupt or unreadable snapshot behaves like a miss and gets rewritten later.
            try {
                snapshotStore.delete()
            } catch (_: Exception) {
                // The safe answer below must not depend on cleanup succeeding.
            }
            StoredRecoWeights()
        }
        _weights.value = loaded
        return loaded
    }

    private fun persistLocked(stored: StoredRecoWeights) {
        _weights.value = stored
        try {
            snapshotStore.write(json.encodeToString(StoredRecoWeights.serializer(), stored))
        } catch (exception: Exception) {
            printe(message = "Failed to persist the reco weights", exception = exception)
        }
    }

    companion object {
        const val WEIGHTS_FILE_NAME = "reco_weights.json"

        @Volatile
        private var instance: RecoWeightsStore? = null

        fun getInstance(context: Context): RecoWeightsStore =
            instance ?: synchronized(this) {
                instance ?: RecoWeightsStore(context).also { instance = it }
            }
    }
}
