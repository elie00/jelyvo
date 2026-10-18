package ca.ilianokokoro.umihi.music.core.reco

import android.content.Context
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printe
import ca.ilianokokoro.umihi.music.core.managers.NotInterestedStore
import ca.ilianokokoro.umihi.music.core.playback.hourOfDay
import ca.ilianokokoro.umihi.music.models.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.ZoneId

/** Day- and bucket-stamped snapshot of the context mix; regenerated when either moves on. */
@Serializable
data class ContextMixCache(
    /** Local-time epoch day ([dailyMixEpochDay]) the mix was generated for. */
    val epochDay: Long,
    /** The slot ([contextMixBucket]) the mix was generated for. */
    val bucket: DayBucket,
    val generatedAtMs: Long,
    val songs: List<Song>,
)

/** The context mix currently on offer: which slot it belongs to, and its queue. */
data class ContextMix(
    val bucket: DayBucket,
    val songs: List<Song>,
)

/**
 * Owns the "morning/evening mix" of the Home screen: the daily-mix pipeline re-run with the
 * context-heavy weights ([RecoWeights.CONTEXT]) and a bucket-biased candidate pool, cached as a
 * JSON file per (day, slot). Outside both slots the card simply does not exist, so there is no
 * periodic worker: Home opening inside a slot is the only trigger.
 */
class ContextMixRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val inputsProvider = MixGenerationInputsProvider.getInstance(appContext)
    private val hydrateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val generationMutex = Mutex()

    /**
     * Returns the mix of the current slot, generating (and caching) it when the cached one
     * belongs to another day or slot. Null outside the morning/evening slots or when generation
     * fails or comes back too small — Home hides the card in every one of those cases.
     */
    suspend fun currentMix(
        nowMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): ContextMix? = withContext(Dispatchers.IO) {
        generationMutex.withLock {
            val bucket = contextMixBucket(hourOfDay(nowMs, zone)) ?: return@withLock null
            val today = dailyMixEpochDay(nowMs, zone)
            val cached = loadCache()
            if (cached != null && cached.epochDay == today && cached.bucket == bucket &&
                cached.songs.isNotEmpty()
            ) {
                return@withLock ContextMix(bucket = bucket, songs = cached.songs)
            }
            regenerate(nowMs, today, bucket)
        }
    }

    private suspend fun regenerate(nowMs: Long, epochDay: Long, bucket: DayBucket): ContextMix? {
        return try {
            // Same guard as the daily mix: never resurface a rejected track from a cold start.
            NotInterestedStore.hydrate(appContext, hydrateScope)

            val inputs = inputsProvider.load(nowMs) ?: return null
            val profile = inputs.profile

            // Same daily feedback pass as the daily mix (idempotent per local day).
            val weightsStore = RecoWeightsStore.getInstance(appContext)
            weightsStore.maybeTune(profile, nowMs)

            val mix = buildDailyMix(
                profile = profile,
                candidates = biasCandidatesToBucket(
                    candidates = inputs.candidates,
                    profile = profile,
                    bucket = bucket,
                ),
                notInterestedIds = NotInterestedStore.hiddenIds,
                nowMs = nowMs,
                targetSize = CONTEXT_MIX_TARGET_SIZE,
                weights = weightsStore.current().context,
                dayBucket = bucket,
            )
            if (mix.size >= DAILY_MIX_MIN_SONGS) {
                persist(
                    ContextMixCache(
                        epochDay = epochDay,
                        bucket = bucket,
                        generatedAtMs = nowMs,
                        songs = mix,
                    )
                )
                ContextMix(bucket = bucket, songs = mix)
            } else {
                // Too small to feel like a mix: not cached either, so the next open retries.
                null
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            printe(message = "Failed to generate the context mix", exception = exception)
            null
        }
    }

    private fun loadCache(): ContextMixCache? {
        val file = cacheFile()
        if (!file.exists()) return null
        return try {
            json.decodeFromString(ContextMixCache.serializer(), file.readText())
        } catch (exception: Exception) {
            // A corrupt snapshot behaves like a miss and gets rewritten by the next generation.
            runCatching { file.delete() }
            null
        }
    }

    private fun persist(cache: ContextMixCache) {
        try {
            cacheFile().writeText(json.encodeToString(ContextMixCache.serializer(), cache))
        } catch (exception: Exception) {
            printe(message = "Failed to persist the context mix", exception = exception)
        }
    }

    private fun cacheFile() = File(appContext.filesDir, CACHE_FILE_NAME)

    companion object {
        const val CACHE_FILE_NAME = "context_mix.json"

        @Volatile
        private var instance: ContextMixRepository? = null

        fun getInstance(context: Context): ContextMixRepository =
            instance ?: synchronized(this) {
                instance ?: ContextMixRepository(context).also { instance = it }
            }
    }
}
