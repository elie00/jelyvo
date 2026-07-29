package ca.ilianokokoro.umihi.music.core.workers

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Enqueues the periodic [DailyMixWorker] pre-generating the Home "daily mix". */
object DailyMixScheduler {
    private const val WORK_NAME = "daily_mix_generation"
    private const val REPEAT_INTERVAL_HOURS = 24L

    fun schedule(context: Context) {
        // No network constraint: an offline run still produces a valid history-only mix.
        val request = PeriodicWorkRequestBuilder<DailyMixWorker>(
            REPEAT_INTERVAL_HOURS,
            TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
