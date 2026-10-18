package ca.ilianokokoro.umihi.music.core.youtube

import ca.ilianokokoro.umihi.music.core.domain.ArtistRef
import ca.ilianokokoro.umihi.music.core.domain.Track
import ca.ilianokokoro.umihi.music.core.domain.parseTrackDurationMillis
import kotlinx.serialization.json.*

private val VIDEO_ID_REGEX = Regex("[A-Za-z0-9_-]{11}")
private val DURATION_REGEX = Regex("""\d{1,2}:\d{2}(?::\d{2})?""")

/**
 * Every `musicResponsiveListItemRenderer` row of an InnerTube response, in document order and
 * without duplicate ids. Search, home and library responses all list tracks with this renderer,
 * so the Watch and macOS clients read them through this one parser.
 */
fun parseMusicListItems(raw: String): List<Track> =
    Json.parseToJsonElement(raw).objects()
        .mapNotNull { it["musicResponsiveListItemRenderer"] as? JsonObject }
        .mapNotNull(::parseMusicListItem)
        .distinctBy(Track::id)
        .toList()

/** One track row, or null when it carries no valid video id or no title. */
fun parseMusicListItem(renderer: JsonObject): Track? {
    val id = (renderer["playlistItemData"] as? JsonObject)?.text("videoId")
        ?: renderer.objects().mapNotNull { (it["watchEndpoint"] as? JsonObject)?.text("videoId") }.firstOrNull()
        ?: return null
    if (!VIDEO_ID_REGEX.matches(id)) return null
    val columns = (renderer["flexColumns"] as? JsonArray).orEmpty()
    val title = columns.firstOrNull()?.objects()?.firstOrNull { it["runs"] is JsonArray }
        ?.get("runs")?.let { it as JsonArray }?.joinToString("") { (it as? JsonObject)?.text("text").orEmpty() }
        ?.takeIf { it.isNotBlank() } ?: return null
    val subtitle = columns.getOrNull(1)?.objects().orEmpty().filter { it.text("text") != null }.toList()
    val artist = subtitle.firstOrNull { run -> run.objects().any { (it["browseEndpoint"] as? JsonObject)?.text("browseId")?.startsWith("UC") == true } }
        ?: subtitle.firstOrNull { it.text("text")?.let { text -> text.isNotBlank() && text.trim() != "•" } == true }
    val artistId = artist?.objects()?.firstNotNullOfOrNull { (it["browseEndpoint"] as? JsonObject)?.text("browseId") }
        ?.takeIf { it.startsWith("UC") }
    val artwork = renderer["thumbnail"]?.objects()?.filter { it.text("url") != null }
        ?.maxByOrNull { (it["width"] as? JsonPrimitive)?.intOrNull ?: 0 }?.text("url")
    // Library rows put the duration in a fixed column; search rows append it to the subtitle.
    val duration = renderer["fixedColumns"]?.objects()?.mapNotNull { it.text("text")?.let(::parseTrackDurationMillis) }?.firstOrNull()
        ?: subtitle.mapNotNull { it.text("text")?.trim()?.takeIf(DURATION_REGEX::matches)?.let(::parseTrackDurationMillis) }.firstOrNull()
    val explicit = renderer.objects().any { it.text("iconType") == "MUSIC_EXPLICIT_BADGE" }
    return Track(
        id = id,
        title = title,
        artist = ArtistRef(id = artistId, name = artist?.text("text").orEmpty()),
        artworkUrl = artwork,
        durationMs = duration,
        explicit = explicit,
        sourceUri = "https://www.youtube.com/watch?v=$id",
    )
}

internal fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull

internal fun JsonElement.objects(depth: Int = 0): Sequence<JsonObject> = sequence {
    if (depth > 64) return@sequence
    when (this@objects) {
        is JsonObject -> { yield(this@objects); values.forEach { yieldAll(it.objects(depth + 1)) } }
        is JsonArray -> forEach { yieldAll(it.objects(depth + 1)) }
        else -> Unit
    }
}
