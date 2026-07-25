package ca.ilianokokoro.umihi.music.core.youtube

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** A YouTube video id is always 11 characters of the URL-safe base64 alphabet. */
private val VIDEO_ID_REGEX = Regex("[A-Za-z0-9_-]{11}")

/**
 * First path segments that carry the id in the path instead of a `v=` parameter: `/v/` and
 * `/embed/` are advertised by MainActivity's VIEW intent-filter, `/shorts/` only reaches us
 * through the share sheet.
 */
private val ID_IN_PATH_SEGMENTS = setOf("v", "embed", "shorts")

/**
 * Resolves the video id of a shared or opened YouTube link. Kept free of android.net.Uri so the
 * link forms the manifest advertises can be pinned by JVM unit tests — android.* throws
 * "not mocked" there, which is how /embed/ and /v/ silently resolved to nothing for so long.
 */
internal fun parseYoutubeVideoId(url: String): String? {
    val httpUrl = url.toHttpUrlOrNull() ?: return null
    // HttpUrl lower-cases the host; matching it exactly instead of with `contains` also keeps
    // look-alike hosts such as youtu.be.example.com out.
    val host = httpUrl.host
    // A trailing slash yields an empty last segment, so index positions are only stable once the
    // empty segments are dropped.
    val segments = httpUrl.pathSegments.filter { it.isNotEmpty() }

    val candidate = when {
        host == "youtu.be" -> segments.firstOrNull()

        host == "youtube.com" || host.endsWith(".youtube.com") ->
            if (segments.firstOrNull() in ID_IN_PATH_SEGMENTS) {
                segments.getOrNull(1)
            } else {
                httpUrl.queryParameter("v")
            }

        else -> null
    }

    // /embed/videoseries?list=... is a playlist embed, and "videoseries" is exactly 11 legal
    // characters, so the shape check alone would hand it on as a video id and fire a doomed request.
    return candidate?.takeIf { it != "videoseries" && VIDEO_ID_REGEX.matches(it) }
}
