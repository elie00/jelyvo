package ca.ilianokokoro.umihi.music.wearapp

import ca.ilianokokoro.umihi.music.core.YoutubeExtractor
import ca.ilianokokoro.umihi.music.core.domain.Track
import ca.ilianokokoro.umihi.music.core.domain.formatTrackDuration
import ca.ilianokokoro.umihi.music.core.youtube.YoutubeApiClient
import ca.ilianokokoro.umihi.music.core.youtube.YoutubeApiConstants
import ca.ilianokokoro.umihi.music.core.youtube.parseMusicListItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import java.io.IOException
import org.schabi.newpipe.extractor.ServiceList

/** Watch-side facade over the shared InnerTube layer of `:music-youtube`. */
class WatchYoutubeClient {
    suspend fun searchSongs(query: String): List<WatchSong> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        parseSearchResponse(YoutubeApiClient.search(query.trim(), SONGS_FILTER))
    }

    suspend fun homeSongs(seedArtist: String? = null): List<WatchSong> = withContext(Dispatchers.IO) {
        val directSongs = parseSearchResponse(YoutubeApiClient.browse(HOME_BROWSE_ID, settings = null))
        if (directSongs.isNotEmpty()) directSongs
        else searchSongs(seedArtist?.takeIf(String::isNotBlank) ?: "Nouveautés musique")
    }

    suspend fun resolveAudioUrl(videoId: String): String = withContext(Dispatchers.IO) {
        val response = YoutubeApiClient.getPlayerInfo(
            videoId = videoId,
            client = YoutubeApiConstants.Client.ANDROID_VR,
        )
        val root = Json.parseToJsonElement(response).jsonObject
        val status = root.objectAt("playabilityStatus")?.stringAt("status")
        val formats = root.objectAt("streamingData")
            ?.get("adaptiveFormats")
            ?.asArray()
            .orEmpty()
            .mapNotNull(JsonElement::asObject)
            .filter { format ->
                format.stringAt("mimeType")?.startsWith("audio/") == true &&
                    !format.stringAt("url").isNullOrBlank()
            }
        val best = formats.maxWithOrNull(
            compareBy<JsonObject> {
                when {
                    it.stringAt("mimeType")?.contains("opus") == true -> 3
                    it.stringAt("mimeType")?.contains("mp4a") == true -> 2
                    else -> 1
                }
            }.thenBy { it.intAt("bitrate") ?: 0 },
        )
        best?.stringAt("url") ?: resolveAudioWithNewPipe(videoId, status)
    }

    private fun resolveAudioWithNewPipe(videoId: String, fastStatus: String?): String {
        YoutubeExtractor.ensureInitialized()
        return runCatching {
            ServiceList.YouTube
                .getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
                .apply { fetchPage() }
                .audioStreams
                .filter { it.content.isNotBlank() }
                .maxByOrNull { it.averageBitrate }
                ?.content
                ?: throw IOException("Aucun flux audio compatible")
        }.getOrElse { failure ->
            throw IOException(
                "Lecture indisponible${fastStatus?.let { " ($it)" }.orEmpty()}",
                failure,
            )
        }
    }

    internal fun parseSearchResponse(response: String): List<WatchSong> =
        parseMusicListItems(response)
            .take(MAX_RESULTS)
            .map(Track::toWatchSong)

    private companion object {
        const val HOME_BROWSE_ID = "FEmusic_home"
        const val SONGS_FILTER = "EgWKAQIIAWoKEAkQBRAKEAMQBA=="
        const val MAX_RESULTS = 30
    }
}

private fun Track.toWatchSong() = WatchSong(
    videoId = id,
    title = title,
    artist = artist.name.ifBlank { "Artiste inconnu" },
    artworkUrl = artworkUrl.orEmpty(),
    durationText = formatTrackDuration(durationMs),
    isExplicit = explicit,
)

private fun JsonElement.asObject(): JsonObject? = this as? JsonObject
private fun JsonElement.asArray(): JsonArray? = this as? JsonArray
private fun JsonObject.objectAt(key: String): JsonObject? = get(key)?.asObject()
private fun JsonObject.stringAt(key: String): String? =
    (get(key) as? JsonPrimitive)?.contentOrNull
private fun JsonObject.intAt(key: String): Int? =
    (get(key) as? JsonPrimitive)?.intOrNull
