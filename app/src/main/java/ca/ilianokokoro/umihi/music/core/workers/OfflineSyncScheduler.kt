package ca.ilianokokoro.umihi.music.core.workers

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Enqueues / cancels the periodic [OfflineSyncWorker] backing the "smart offline" auto-download.
 * The Wi-Fi-only preference maps to the work's network constraint, so a change to it must go
 * through [reschedule] (an UPDATE) rather than the default KEEP schedule.
 */
object OfflineSyncScheduler {
    private const val WORK_NAME = "offline_sync"
    private const val WORK_NAME_NOW = "offline_sync_now"
    private const val REPEAT_INTERVAL_HOURS = 12L

    private fun constraints(wifiOnly: Boolean) = Constraints(
        requiredNetworkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED,
        requiresStorageNotLow = true
    )

    /** (Re)schedules the periodic sync, leaving an existing schedule untouched (KEEP). */
    fun schedule(context: Context, wifiOnly: Boolean) {
        enqueue(context, wifiOnly, ExistingPeriodicWorkPolicy.KEEP)
    }

    /** Replaces the periodic sync so a changed Wi-Fi-only constraint takes effect immediately. */
    fun reschedule(context: Context, wifiOnly: Boolean) {
        enqueue(context, wifiOnly, ExistingPeriodicWorkPolicy.UPDATE)
    }

    private fun enqueue(context: Context, wifiOnly: Boolean, policy: ExistingPeriodicWorkPolicy) {
        val request = PeriodicWorkRequestBuilder<OfflineSyncWorker>(
            REPEAT_INTERVAL_HOURS,
            TimeUnit.HOURS
        ).setConstraints(constraints(wifiOnly)).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, policy, request)
    }

    /** Fires a one-off sync now (e.g. right after the user enables the feature). */
    fun triggerNow(context: Context, wifiOnly: Boolean) {
        val request = OneTimeWorkRequestBuilder<OfflineSyncWorker>()
            .setConstraints(constraints(wifiOnly))
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME_NOW, ExistingWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
