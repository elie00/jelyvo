package ca.ilianokokoro.umihi.music.core.youtube

import ca.ilianokokoro.umihi.music.core.helpers.UmihiHelper.safeObject
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.AudioQuality
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Selects by rank instead of fixed bitrate thresholds because YouTube's format ladder varies per
 * client and track. High deliberately preserves the former maxByOrNull behaviour.
 */
internal fun <T> selectAudioStream(
    streams: List<T>,
    quality: AudioQuality,
    bitrateOf: (T) -> Int,
): T? {
    if (streams.isEmpty()) return null
    return when (quality) {
        AudioQuality.Low -> streams.minByOrNull(bitrateOf)
        AudioQuality.Normal -> streams.sortedBy(bitrateOf)[(streams.size - 1) / 2]
        AudioQuality.High -> streams.maxByOrNull(bitrateOf)
    }
}

internal fun selectAudioStreamUrl(
    adaptiveFormats: JsonArray?,
    quality: AudioQuality,
): String? {
    val audioFormats = adaptiveFormats.orEmpty()
        .mapNotNull { it.safeObject() }
        .filter { it["url"]?.jsonPrimitive?.contentOrNull?.isNotBlank() == true }
        .filter {
            it["mimeType"]
                ?.jsonPrimitive
                ?.contentOrNull
                ?.startsWith("audio/", ignoreCase = true) == true
        }

    return selectAudioStream(audioFormats, quality) { format: JsonObject ->
        format["bitrate"]?.jsonPrimitive?.intOrNull ?: 0
    }?.get("url")?.jsonPrimitive?.contentOrNull
}
