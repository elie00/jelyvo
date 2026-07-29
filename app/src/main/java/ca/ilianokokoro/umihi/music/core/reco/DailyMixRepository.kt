package ca.ilianokokoro.umihi.music.core.reco

import android.content.Context
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printe
import ca.ilianokokoro.umihi.music.core.managers.NotInterestedStore
import ca.ilianokokoro.umihi.music.models.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.ZoneId

/**
 * Cached day-stamped snapshot of the daily mix: the id list plus enough metadata to rebuild the
 * queue without any lookup. Regenerated whenever the local date moves on.
 */
@Serializable
data class DailyMixCache(
    /** Local-time epoch day ([dailyMixEpochDay]) the mix was generated for. */
    val epochDay: Long,
    val generatedAtMs: Long,
    val songs: List<Song>,
)

/**
 * Owns the "daily mix" of the Home screen: one generated queue per local day, cached as a JSON
 * file in `filesDir` so reopening the app costs nothing. Generation happens either through the
 * periodic [ca.ilianokokoro.umihi.music.core.workers.DailyMixWorker] or on the fly when Home
 * opens with no mix for today; a mutex keeps those two paths from generating twice.
 */
class DailyMixRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val inputsProvider = MixGenerationInputsProvider.getInstance(appContext)
    private val hydrateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val generationMutex = Mutex()

    /**
     * Returns today's mix, generating (and caching) it when the cached one is missing or stale.
     * Never throws: a failed generation yields an empty list and Home simply hides the card.
     */
    suspend fun todaysMix(
        nowMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Song> = generationMutex.withLock {
        val today = dailyMixEpochDay(nowMs, zone)
        val cached = loadCache()
        if (cached != null && cached.epochDay == today && cached.songs.isNotEmpty()) {
            return cached.songs
        }
        regenerate(nowMs, today)
    }

    private suspend fun regenerate(nowMs: Long, epochDay: Long): List<Song> {
        return try {
            // The veto list normally hydrates with the playback service; do it here too so a
            // background generation never resurfaces a rejected track.
            NotInterestedStore.hydrate(appContext, hydrateScope)

            val inputs = inputsProvider.load(nowMs) ?: return emptyList()
            val profile = inputs.profile

            // Daily feedback pass, then rank with whatever the tuner settled on.
            val weightsStore = RecoWeightsStore.getInstance(appContext)
            weightsStore.maybeTune(profile, nowMs)

            val mix = buildDailyMix(
                profile = profile,
                candidates = inputs.candidates,
                notInterestedIds = NotInterestedStore.hiddenIds,
                nowMs = nowMs,
                weights = weightsStore.current().daily,
            )
            if (mix.size >= DAILY_MIX_MIN_SONGS) {
                persist(DailyMixCache(epochDay = epochDay, generatedAtMs = nowMs, songs = mix))
                mix
            } else {
                // Too small to feel like a mix: not cached either, so the next open retries.
                emptyList()
            }
        } catch (exception: Exception) {
            printe(message = "Failed to generate the daily mix", exception = exception)
            emptyList()
        }
    }

    private fun loadCache(): DailyMixCache? {
        val file = cacheFile()
        if (!file.exists()) return null
        return try {
            json.decodeFromString(DailyMixCache.serializer(), file.readText())
        } catch (exception: Exception) {
            // A corrupt snapshot behaves like a miss and gets rewritten by the next generation.
            runCatching { file.delete() }
            null
        }
    }

    private fun persist(cache: DailyMixCache) {
        try {
            cacheFile().writeText(json.encodeToString(DailyMixCache.serializer(), cache))
        } catch (exception: Exception) {
            printe(message = "Failed to persist the daily mix", exception = exception)
        }
    }

    private fun cacheFile() = File(appContext.filesDir, CACHE_FILE_NAME)

    companion object {
        const val CACHE_FILE_NAME = "daily_mix.json"

        @Volatile
        private var instance: DailyMixRepository? = null

        fun getInstance(context: Context): DailyMixRepository =
            instance ?: synchronized(this) {
                instance ?: DailyMixRepository(context).also { instance = it }
            }
    }
}
