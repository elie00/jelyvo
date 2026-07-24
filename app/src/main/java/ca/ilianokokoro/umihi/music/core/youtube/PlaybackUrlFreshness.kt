package ca.ilianokokoro.umihi.music.core.youtube

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private const val STREAM_EXPIRY_SAFETY_SECONDS = 5L * 60L

/**
 * YouTube CDN URLs carry their expiry as epoch seconds. Checking it locally avoids a blocking HEAD
 * round-trip on every cached playback while the margin prevents starting with an almost-dead URL.
 */
internal fun isPlaybackUrlFresh(
    url: String,
    nowEpochSeconds: Long = System.currentTimeMillis() / 1000L,
): Boolean {
    val expiresAt = url.toHttpUrlOrNull()
        ?.queryParameter("expire")
        ?.toLongOrNull()
        ?: return false
    return expiresAt - nowEpochSeconds > STREAM_EXPIRY_SAFETY_SECONDS
}
