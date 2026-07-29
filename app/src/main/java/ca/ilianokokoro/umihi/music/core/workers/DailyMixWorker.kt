package ca.ilianokokoro.umihi.music.core.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printd
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printe
import ca.ilianokokoro.umihi.music.core.reco.DailyMixRepository
import kotlin.coroutines.cancellation.CancellationException

/**
 * Periodic pre-generation of the "daily mix" so opening the Home tab finds a fresh queue in the
 * cache. [DailyMixRepository.todaysMix] is a no-op when today's mix already exists, and Home
 * generates on the fly when this worker has not run yet — the worker is an optimization, never
 * a requirement. It deliberately has no network constraint: without connectivity the generation
 * degrades to the local history, which is still a valid mix.
 */
class DailyMixWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val mix = DailyMixRepository.getInstance(applicationContext).todaysMix()
            printd("Daily mix worker: today's mix has ${mix.size} tracks")
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            printe(message = "Daily mix generation failed", exception = e)
            Result.retry()
        }
    }
}
