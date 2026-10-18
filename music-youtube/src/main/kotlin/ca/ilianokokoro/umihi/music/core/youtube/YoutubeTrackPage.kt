package ca.ilianokokoro.umihi.music.core.youtube

import ca.ilianokokoro.umihi.music.core.domain.Track
import kotlinx.serialization.json.*

/** A page of account tracks, shared without Android or UI dependencies. */
data class YoutubeTrackPage(val tracks: List<Track>, val continuation: String?, val signedOut: Boolean)

fun parseYoutubeTrackPage(raw: String): YoutubeTrackPage {
    val root = Json.parseToJsonElement(raw)
    val objects = root.objects().toList()
    val tracks = objects.mapNotNull { it["musicResponsiveListItemRenderer"] as? JsonObject }
        .mapNotNull(::parseMusicListItem)
    val continuation = objects.firstNotNullOfOrNull {
        (it["nextContinuationData"] as? JsonObject)?.text("continuation")
            ?: (it["continuationCommand"] as? JsonObject)?.text("token")
    }
    val signedOut = objects.any {
        (it["loggedOut"] as? JsonPrimitive)?.booleanOrNull == true || it["signInEndpoint"] != null
    }
    return YoutubeTrackPage(tracks, continuation, signedOut && tracks.isEmpty())
}
