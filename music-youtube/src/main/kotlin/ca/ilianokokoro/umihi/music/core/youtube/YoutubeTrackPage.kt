package ca.ilianokokoro.umihi.music.core.youtube

import ca.ilianokokoro.umihi.music.core.domain.ArtistRef
import ca.ilianokokoro.umihi.music.core.domain.Track
import ca.ilianokokoro.umihi.music.core.domain.parseTrackDurationMillis
import kotlinx.serialization.json.*

/** A page of account tracks, shared without Android or UI dependencies. */
data class YoutubeTrackPage(val tracks: List<Track>, val continuation: String?, val signedOut: Boolean)

fun parseYoutubeTrackPage(raw: String): YoutubeTrackPage {
    val root = Json.parseToJsonElement(raw)
    val objects = root.objects().toList()
    val tracks = objects.mapNotNull { it["musicResponsiveListItemRenderer"] as? JsonObject }
        .mapNotNull { renderer ->
            val id = (renderer["playlistItemData"] as? JsonObject)?.text("videoId")
                ?: renderer.objects().mapNotNull { (it["watchEndpoint"] as? JsonObject)?.text("videoId") }.firstOrNull()
                ?: return@mapNotNull null
            if (!Regex("[A-Za-z0-9_-]{11}").matches(id)) return@mapNotNull null
            val columns = (renderer["flexColumns"] as? JsonArray).orEmpty()
            val title = columns.firstOrNull()?.objects()?.firstOrNull { it["runs"] is JsonArray }
                ?.get("runs")?.let { it as JsonArray }?.joinToString("") { (it as? JsonObject)?.text("text").orEmpty() }
                ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val subtitle = columns.getOrNull(1)?.objects().orEmpty().filter { it.text("text") != null }.toList()
            val artist = subtitle.firstOrNull { run -> run.objects().any { (it["browseEndpoint"] as? JsonObject)?.text("browseId")?.startsWith("UC") == true } }
                ?: subtitle.firstOrNull { it.text("text")?.let { text -> text.isNotBlank() && text.trim() != "•" } == true }
            val artwork = renderer["thumbnail"]?.objects()?.filter { it.text("url") != null }
                ?.maxByOrNull { (it["width"] as? JsonPrimitive)?.intOrNull ?: 0 }?.text("url")
            val duration = renderer["fixedColumns"]?.objects()?.mapNotNull { it.text("text")?.let(::parseTrackDurationMillis) }?.firstOrNull()
            Track(id = id, title = title, artist = ArtistRef(name = artist?.text("text").orEmpty()), artworkUrl = artwork, durationMs = duration,
                sourceUri = "https://www.youtube.com/watch?v=$id")
        }
    val continuation = objects.firstNotNullOfOrNull {
        (it["nextContinuationData"] as? JsonObject)?.text("continuation")
            ?: (it["continuationCommand"] as? JsonObject)?.text("token")
    }
    val signedOut = objects.any {
        (it["loggedOut"] as? JsonPrimitive)?.booleanOrNull == true || it["signInEndpoint"] != null
    }
    return YoutubeTrackPage(tracks, continuation, signedOut && tracks.isEmpty())
}

private fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull
private fun JsonElement.objects(depth: Int = 0): Sequence<JsonObject> = sequence {
    if (depth > 64) return@sequence
    when (this@objects) {
        is JsonObject -> { yield(this@objects); values.forEach { yieldAll(it.objects(depth + 1)) } }
        is JsonArray -> forEach { yieldAll(it.objects(depth + 1)) }
        else -> Unit
    }
}
