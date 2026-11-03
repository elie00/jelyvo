package ca.ilianokokoro.umihi.music.core.diagnostics

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Where in the chain a track failed. The first two have fallbacks; a player failure reached the listener. */
enum class FailureSource { AndroidVr, NewPipe, Player }

/**
 * One failure. There is deliberately no track or account here: the log says how playback is doing,
 * never what somebody listens to, so it can go in a shared diagnostic.
 */
@Serializable
data class PlaybackFailure(
    val timeMs: Long,
    val source: FailureSource,
    val code: String,
    val detail: String = "",
)

/** What went wrong, in the terms the listener can act on. */
enum class FailureKind { Network, RefusedBySource, Other }

object FailureRules {
    private val URL = Regex("""\b[a-z][a-z0-9+.-]*://\S+""", RegexOption.IGNORE_CASE)
    private val QUERY = Regex("""[?&][\w-]+=\S*""")
    private val VIDEO_ID = Regex("""\b[A-Za-z0-9_-]{11}\b(?=\s*:)|(?<=v=)[A-Za-z0-9_-]{11}""")
    private val REFUSED = Regex(
        """LOGIN_REQUIRED|UNPLAYABLE|ERROR_CODE_IO_BAD_HTTP_STATUS|HTTP 40[13]|sign in|not a bot""",
        RegexOption.IGNORE_CASE,
    )
    private const val MAX_DETAIL = 160

    /** How long a client's refusal still explains a player failure that follows it. */
    const val RECENT_MS = 60_000L

    /**
     * Messages can quote the signed address of a stream, which carries the listener's IP and
     * session, or a track's id. Neither belongs in a log meant to be shared.
     */
    fun sanitize(text: String?): String = text.orEmpty()
        .replace(URL, "<url>")
        .replace(QUERY, "")
        .replace(VIDEO_ID, "<id>")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .take(MAX_DETAIL)

    /** Why playback stopped. No connection at all explains everything else, so it comes first. */
    fun classify(
        hasNetwork: Boolean,
        isNetworkError: Boolean,
        errorCode: String,
        recent: List<PlaybackFailure>,
        nowMs: Long,
    ): FailureKind = when {
        !hasNetwork || isNetworkError -> FailureKind.Network
        REFUSED.containsMatchIn(errorCode) -> FailureKind.RefusedBySource
        recent.any {
            nowMs - it.timeMs <= RECENT_MS &&
                it.source != FailureSource.Player &&
                (REFUSED.containsMatchIn(it.code) || REFUSED.containsMatchIn(it.detail))
        } -> FailureKind.RefusedBySource

        else -> FailureKind.Other
    }

    /** Counts per client and the latest failures, for the shared diagnostic. */
    fun summary(failures: List<PlaybackFailure>): String = buildString {
        appendLine("Playback failures: ${failures.size} recorded")
        FailureSource.entries.forEach { source ->
            val ofSource = failures.filter { it.source == source }
            ofSource.lastOrNull()?.let { last ->
                appendLine("  ${source.name}: ${ofSource.size}, last ${timestamp(last.timeMs)} ${last.code}")
            }
        }
        val latest = failures.takeLast(SUMMARY_LINES)
        if (latest.isNotEmpty()) appendLine("Latest:")
        latest.forEach { failure ->
            val detail = failure.detail.takeIf(String::isNotBlank)?.let { " - $it" }.orEmpty()
            appendLine("  ${timestamp(failure.timeMs)} ${failure.source.name} ${failure.code}$detail")
        }
    }.trim()

    private fun timestamp(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(ms))

    private const val SUMMARY_LINES = 12
}

/** Keeps the latest failures on the device. Nothing leaves it unless the listener shares a report. */
object PlaybackFailures {
    const val MAX_ENTRIES = 50

    private const val PREFERENCES = "playback_failures"
    private const val KEY_ENTRIES = "entries"
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()
    private var preferences: SharedPreferences? = null
    private var entries: List<PlaybackFailure> = emptyList()

    fun init(context: Context) = synchronized(lock) {
        val store = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        preferences = store
        // Whatever cannot be read is only a diagnostic: it never gets in the way of playing music.
        entries = runCatching {
            json.decodeFromString<List<PlaybackFailure>>(store.getString(KEY_ENTRIES, "[]").orEmpty())
        }.getOrDefault(emptyList())
    }

    fun record(
        source: FailureSource,
        code: String,
        detail: String? = null,
        nowMs: Long = System.currentTimeMillis(),
    ) = synchronized(lock) {
        val failure = PlaybackFailure(
            timeMs = nowMs,
            source = source,
            code = FailureRules.sanitize(code),
            detail = FailureRules.sanitize(detail),
        )
        entries = (entries + failure).takeLast(MAX_ENTRIES)
        preferences?.edit()?.putString(KEY_ENTRIES, json.encodeToString(entries))?.apply()
    }

    fun recent(): List<PlaybackFailure> = synchronized(lock) { entries }

    fun clear() = synchronized(lock) {
        entries = emptyList()
        preferences?.edit()?.remove(KEY_ENTRIES)?.apply()
    }
}
