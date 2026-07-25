package ca.ilianokokoro.umihi.music.core.cast

import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.data.datasources.local.LocalAudioDataSource
import ca.ilianokokoro.umihi.music.models.Song
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Cast castability and content-type rules. Deliberately free of android.* and media3 types so the
 * whole decision surface is pinned by JVM unit tests — android.* throws "not mocked" there, and
 * both a mis-typed stream and a local file smuggled onto a receiver fail invisibly on the TV.
 */

/**
 * Last-resort contentType when neither the format nor the URL exposed one. A wrong guess is not
 * silent: the receiver refuses the load rather than playing garbage.
 */
internal const val DEFAULT_CAST_CONTENT_TYPE = "audio/mp4"

/**
 * True when this track can be handed to a Chromecast at all. Only streamed YouTube tracks can: a
 * receiver fetches over HTTP and can open neither a MediaStore content:// URI nor an app-private
 * filesDir download (the FileProvider that could have exposed one was removed in 026e614).
 *
 * `downloaded` is intentionally NOT part of this test. A downloaded track still has a real
 * youtubeId, and getSongPlayerUrl(allowLocal = false) resolves it to a network stream — it is the
 * FILE that cannot cast, not the track.
 *
 * [Song.isLocal] is @Ignore/transient and can be lost across a Bundle round-trip, so the MediaStore
 * id prefix is checked too.
 */
fun Song.isCastable(): Boolean =
    !isLocal &&
        youtubeId.isNotBlank() &&
        !youtubeId.startsWith(LocalAudioDataSource.LOCAL_ID_PREFIX)

/**
 * The contentType to declare in the Cast MediaInfo. YouTube reports a full RFC-6381 type
 * (`audio/webm; codecs="opus"`); the parameters are dropped because a single non-manifest URL is
 * played by the default receiver through an HTML5 src, which sniffs the container itself and is
 * stricter about unknown parameters than about a bare type.
 *
 * Order of trust: the format object that produced the URL, then the URL's own `mime` parameter
 * (googlevideo carries one, but nothing in this repo proves its shape — fallback only), then
 * [DEFAULT_CAST_CONTENT_TYPE].
 */
fun castContentType(formatMimeType: String?, streamUrl: String): String =
    formatMimeType.toBareAudioType()
        ?: streamUrl.toHttpUrlOrNull()?.queryParameter("mime").toBareAudioType()
        ?: DEFAULT_CAST_CONTENT_TYPE

private fun String?.toBareAudioType(): String? =
    this?.substringBefore(';')?.trim()?.lowercase()?.takeIf { it.startsWith("audio/") }

/**
 * Tripwire for the one bug this whole layer exists to prevent: a MediaItem still carrying
 * Song.mediaItem's watch-page URI reaching the receiver, which would serialise an HTML page into a
 * MediaInfo and fail with no useful message.
 */
fun isUnresolvedWatchUrl(uri: String): Boolean =
    uri.startsWith(Constants.YoutubeApi.YOUTUBE_URL_PREFIX)
