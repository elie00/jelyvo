package ca.ilianokokoro.umihi.music.core.helpers

import android.util.Log
import ca.ilianokokoro.umihi.music.BuildConfig
import kotlin.time.measureTimedValue

object LogHelper {
    const val TAG = "JelyvoPrint"

    inline fun <T> benchmark(
        label: String,
        block: () -> T
    ): T {
        val result = measureTimedValue(block)
        printd(tag = "JelyvoBench", message = "$label: ${result.duration.inWholeMilliseconds} ms")
        return result.value
    }

    fun printd(message: String, tag: String = TAG) {
        // Debug logging is stripped from release builds to avoid leaking sensitive data.
        if (BuildConfig.DEBUG) {
            Log.d(tag, message)
        }
    }

    fun printe(message: String, tag: String = TAG, exception: Exception? = null) {
        Log.e(TAG, message, exception)
    }
}
