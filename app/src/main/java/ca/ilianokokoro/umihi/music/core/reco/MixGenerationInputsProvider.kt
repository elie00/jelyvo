package ca.ilianokokoro.umihi.music.core.reco

import android.content.Context
import ca.ilianokokoro.umihi.music.models.Song
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One consistent profile/candidate snapshot shared by every mix generated at the same time. */
data class MixGenerationInputs(
    val profile: TasteProfile,
    val candidates: List<DailyMixCandidate<Song>>,
)

/**
 * Owns the expensive common prefix of daily and context mix generation.
 *
 * Concurrent callers share one in-flight computation: the taste profile is refreshed once and the
 * candidate radios are fetched once. After all callers receive the result, the next generation
 * request starts a fresh computation rather than retaining network data indefinitely.
 */
class MixGenerationInputsProvider internal constructor(
    private val computeInputs: suspend (Long) -> MixGenerationInputs?,
) {
    private val mutex = Mutex()
    private var inFlight: Deferred<MixGenerationInputs?>? = null

    private constructor(context: Context) : this(
        computeInputs = productionComputation(context.applicationContext),
    )

    suspend fun load(nowMs: Long): MixGenerationInputs? {
        val (deferred, ownsComputation) = mutex.withLock {
            inFlight?.let { return@withLock it to false }

            CompletableDeferred<MixGenerationInputs?>().also { created ->
                inFlight = created
            } to true
        }

        if (ownsComputation) {
            check(deferred is CompletableDeferred)
            try {
                deferred.complete(computeInputs(nowMs))
            } catch (throwable: Throwable) {
                deferred.completeExceptionally(throwable)
            } finally {
                mutex.withLock {
                    if (inFlight === deferred) inFlight = null
                }
            }
        }

        return deferred.await()
    }

    companion object {
        @Volatile
        private var instance: MixGenerationInputsProvider? = null

        fun getInstance(context: Context): MixGenerationInputsProvider =
            instance ?: synchronized(this) {
                instance ?: MixGenerationInputsProvider(context).also { instance = it }
            }

        private fun productionComputation(
            context: Context,
        ): suspend (Long) -> MixGenerationInputs? {
            val profileRepository = TasteProfileRepository.getInstance(context)
            val candidateSource = DailyMixCandidateSource(context)
            return { nowMs ->
                profileRepository.refreshNow(nowMs)
                profileRepository.profile.value?.let { profile ->
                    MixGenerationInputs(
                        profile = profile,
                        candidates = candidateSource.collect(profile),
                    )
                }
            }
        }
    }
}
