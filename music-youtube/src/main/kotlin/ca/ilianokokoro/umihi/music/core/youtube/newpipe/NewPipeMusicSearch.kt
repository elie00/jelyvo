package ca.ilianokokoro.umihi.music.core.youtube.newpipe

import ca.ilianokokoro.umihi.music.core.YoutubeExtractor
import ca.ilianokokoro.umihi.music.core.domain.ArtistRef
import ca.ilianokokoro.umihi.music.core.domain.Track
import ca.ilianokokoro.umihi.music.core.domain.TrackKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * Anonymous YouTube Music search through NewPipe.
 *
 * The InnerTube client next door reaches richer pages but parses into Android models today; this
 * path returns neutral [Track]s, which is what a non-Android client needs. Signed-in surfaces
 * (library, liked songs) stay out of reach here by design — they need the account cookies.
 */
object NewPipeMusicSearch {
    private const val SONGS_FILTER = "music_songs"

    suspend fun search(query: String, limit: Int = 30): List<Track> = withContext(Dispatchers.IO) {
        YoutubeExtractor.ensureInitialized()

        val extractor = ServiceList.YouTube.getSearchExtractor(
            query,
            listOf(SONGS_FILTER),
            "",
        )
        extractor.fetchPage()

        extractor.initialPage.items
            .filterIsInstance<StreamInfoItem>()
            .take(limit)
            .map { it.toTrack() }
    }
}

private fun StreamInfoItem.toTrack(): Track {
    val videoId = videoIdOf(url)
    return Track(
        id = videoId ?: url,
        title = name.orEmpty(),
        artist = ArtistRef(name = uploaderName.orEmpty()),
        // NewPipe reports seconds, and -1 when the item carries no duration at all.
        durationMs = duration.takeIf { it > 0L }?.times(1_000L),
        artworkUrl = thumbnails.maxByOrNull { it.height }?.url,
        kind = TrackKind.SONG,
        sourceUri = url,
    )
}

/** Pulls the `v=` id out of a watch URL; NewPipe returns full URLs, [Track.id] holds bare ids. */
internal fun videoIdOf(url: String?): String? {
    if (url.isNullOrBlank()) return null
    val marker = "v="
    val start = url.indexOf(marker).takeIf { it >= 0 }?.plus(marker.length) ?: return null
    val end = url.indexOf('&', start).takeIf { it >= 0 } ?: url.length
    return url.substring(start, end).takeIf { it.isNotBlank() }
}
