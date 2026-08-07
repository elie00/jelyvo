package ca.ilianokokoro.umihi.music.core.youtube

import ca.ilianokokoro.umihi.music.core.helpers.UmihiHelper.safeObject
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.VideoQuality
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Picks the best video track that fits under the requested ceiling.
 *
 * YouTube's muxed ladder (`streamingData.formats`) stops at 720p, so anything above it lives in
 * `adaptiveFormats` as a video-only track that has to be merged with a separate audio one. Callers
 * use [needsSeparateAudio] to tell the two situations apart.
 */
internal object VideoQualitySelector {

    /** Above this height YouTube only ships video-only tracks. */
    const val MUXED_MAX_HEIGHT = 720

    fun needsSeparateAudio(quality: VideoQuality): Boolean =
        quality.maxHeight > MUXED_MAX_HEIGHT

    /**
     * The highest video-only track no taller than [quality]. Falls back to the shortest track when
     * every option overshoots, so an unusual ladder still plays rather than failing.
     */
    fun selectVideoOnly(adaptiveFormats: JsonArray?, quality: VideoQuality): JsonObject? {
        val candidates = adaptiveFormats
            ?.mapNotNull { it.safeObject() }
            ?.filter { it.url().isNotBlank() }
            ?.filter { it.mime().startsWith("video/", ignoreCase = true) }
            ?.filter { it.height() != null }
            ?: return null
        if (candidates.isEmpty()) return null

        val withinCeiling = candidates.filter { (it.height() ?: 0) <= quality.maxHeight }
        return withinCeiling.maxByOrNull { it.rank() }
            ?: candidates.minByOrNull { it.height() ?: Int.MAX_VALUE }
    }

    /** The muxed entry to use when the ceiling stays inside YouTube's progressive ladder. */
    fun selectMuxed(formats: JsonArray?, quality: VideoQuality): JsonObject? {
        val candidates = formats
            ?.mapNotNull { it.safeObject() }
            ?.filter { it.url().isNotBlank() }
            ?.filter { it.mime().startsWith("video/", ignoreCase = true) }
            ?: return null
        if (candidates.isEmpty()) return null

        val ceiling = minOf(quality.maxHeight, MUXED_MAX_HEIGHT)
        val withinCeiling = candidates.filter { (it.height() ?: 0) <= ceiling }
        return withinCeiling.maxByOrNull { it.rank() }
            ?: candidates.minByOrNull { it.height() ?: Int.MAX_VALUE }
    }

    // Height first, bitrate only to break ties: two 1080p tracks differ in codec, not in size.
    private fun JsonObject.rank(): Long =
        (height() ?: 0).toLong() * 1_000_000L + (this["bitrate"]?.jsonPrimitive?.intOrNull ?: 0)

    private fun JsonObject.height(): Int? = this["height"]?.jsonPrimitive?.intOrNull

    private fun JsonObject.url(): String =
        this["url"]?.jsonPrimitive?.contentOrNull.orEmpty()

    private fun JsonObject.mime(): String =
        this["mimeType"]?.jsonPrimitive?.contentOrNull.orEmpty()
}
