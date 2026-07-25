package ca.ilianokokoro.umihi.music.core.youtube

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal const val STREAM_EXPIRY_SAFETY_SECONDS = 5L * 60L

/**
 * Cast queue items can sit on the receiver longer than local prefetch entries. Require enough
 * lifetime that the signed URL remains usable when the receiver eventually reaches it.
 */
internal const val CAST_URL_MIN_LIFETIME_SECONDS = 30L * 60L

/**
 * YouTube CDN URLs carry their expiry as epoch seconds. Checking it locally avoids a blocking HEAD
 * round-trip on every cached playback while the margin prevents starting with an almost-dead URL.
 */
internal fun isPlaybackUrlFresh(
    url: String,
    nowEpochSeconds: Long = System.currentTimeMillis() / 1000L,
    minRemainingSeconds: Long = STREAM_EXPIRY_SAFETY_SECONDS,
): Boolean {
    val expiresAt = url.toHttpUrlOrNull()
        ?.queryParameter("expire")
        ?.toLongOrNull()
        ?: return false
    return expiresAt - nowEpochSeconds > minRemainingSeconds
}
