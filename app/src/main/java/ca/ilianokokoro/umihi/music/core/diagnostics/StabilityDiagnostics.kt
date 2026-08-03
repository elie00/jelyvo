package ca.ilianokokoro.umihi.music.core.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import ca.ilianokokoro.umihi.music.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class StabilityReport(
    val timestampMs: Long,
    val reason: Int,
    val description: String,
    val pssKb: Long,
    val rssKb: Long,
) {
    fun asShareText(): String = buildString {
        appendLine("Jelyvo stability report")
        appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Time (UTC): ${timestampMs.utcTimestamp()}")
        appendLine("Exit: ${exitReasonName(reason)} ($reason)")
        appendLine("Memory: PSS ${pssKb} KB, RSS ${rssKb} KB")
        if (description.isNotBlank()) appendLine("System detail: $description")
    }.trim()
}

/** Captures Android's last unexpected process exit without collecting account or listening data. */
object StabilityDiagnostics {
    fun captureLatestUnexpectedExit(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val manager = context.getSystemService(ActivityManager::class.java) ?: return
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val recordedAt = preferences.getLong(KEY_TIMESTAMP, 0L)
        val candidate = manager
            .getHistoricalProcessExitReasons(context.packageName, 0, HISTORY_LIMIT)
            .asSequence()
            .filter { isUnexpectedExitReason(it.reason) }
            .firstOrNull { it.timestamp > recordedAt }
            ?: return

        preferences.edit()
            .putLong(KEY_TIMESTAMP, candidate.timestamp)
            .putInt(KEY_REASON, candidate.reason)
            .putString(KEY_DESCRIPTION, sanitizeDiagnosticDescription(candidate.description))
            .putLong(KEY_PSS, candidate.pss)
            .putLong(KEY_RSS, candidate.rss)
            .apply()
    }

    fun latest(context: Context): StabilityReport? {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val timestamp = preferences.getLong(KEY_TIMESTAMP, 0L)
        if (timestamp <= 0L) return null
        return StabilityReport(
            timestampMs = timestamp,
            reason = preferences.getInt(KEY_REASON, ApplicationExitInfo.REASON_UNKNOWN),
            description = preferences.getString(KEY_DESCRIPTION, "").orEmpty(),
            pssKb = preferences.getLong(KEY_PSS, 0L),
            rssKb = preferences.getLong(KEY_RSS, 0L),
        )
    }

    private const val PREFERENCES = "stability_diagnostics"
    private const val KEY_TIMESTAMP = "timestamp"
    private const val KEY_REASON = "reason"
    private const val KEY_DESCRIPTION = "description"
    private const val KEY_PSS = "pss"
    private const val KEY_RSS = "rss"
    private const val HISTORY_LIMIT = 12
}

internal fun isUnexpectedExitReason(reason: Int): Boolean = reason in setOf(
    ApplicationExitInfo.REASON_SIGNALED,
    ApplicationExitInfo.REASON_LOW_MEMORY,
    ApplicationExitInfo.REASON_CRASH,
    ApplicationExitInfo.REASON_CRASH_NATIVE,
    ApplicationExitInfo.REASON_ANR,
    ApplicationExitInfo.REASON_INITIALIZATION_FAILURE,
    ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
    ApplicationExitInfo.REASON_DEPENDENCY_DIED,
    ApplicationExitInfo.REASON_FREEZER,
)

internal fun sanitizeDiagnosticDescription(description: String?): String = description
    .orEmpty()
    .replace(Regex("[\\r\\n\\t]+"), " ")
    .replace(Regex("\\s{2,}"), " ")
    .trim()
    .take(MAX_DESCRIPTION_LENGTH)

private fun exitReasonName(reason: Int): String = when (reason) {
    ApplicationExitInfo.REASON_SIGNALED -> "signal"
    ApplicationExitInfo.REASON_LOW_MEMORY -> "low_memory"
    ApplicationExitInfo.REASON_CRASH -> "java_crash"
    ApplicationExitInfo.REASON_CRASH_NATIVE -> "native_crash"
    ApplicationExitInfo.REASON_ANR -> "anr"
    ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "initialization_failure"
    ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "excessive_resource_usage"
    ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "dependency_died"
    ApplicationExitInfo.REASON_FREEZER -> "freezer"
    else -> "unknown"
}

private fun Long.utcTimestamp(): String = SimpleDateFormat(
    "yyyy-MM-dd'T'HH:mm:ss'Z'",
    Locale.ROOT,
).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}.format(Date(this))

private const val MAX_DESCRIPTION_LENGTH = 500
