package ca.ilianokokoro.umihi.music.core.workers

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Enqueues / cancels the periodic [NewReleasesWorker] backing the "new release" notifications. */
object NewReleasesScheduler {
    private const val WORK_NAME = "new_releases_check"
    private const val REPEAT_INTERVAL_HOURS = 12L

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<NewReleasesWorker>(
            REPEAT_INTERVAL_HOURS,
            TimeUnit.HOURS
        ).setConstraints(
            Constraints(requiredNetworkType = NetworkType.CONNECTED)
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
