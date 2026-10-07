package ca.ilianokokoro.umihi.music.core.youtube.newpipe

import ca.ilianokokoro.umihi.music.core.YoutubeExtractor
import ca.ilianokokoro.umihi.music.core.domain.AlbumRef
import ca.ilianokokoro.umihi.music.core.domain.ArtistRef
import ca.ilianokokoro.umihi.music.core.domain.CatalogAlbumPage
import ca.ilianokokoro.umihi.music.core.domain.CatalogArtistPage
import ca.ilianokokoro.umihi.music.core.domain.CatalogSearchFilter
import ca.ilianokokoro.umihi.music.core.domain.CatalogSearchItem
import ca.ilianokokoro.umihi.music.core.domain.Track
import ca.ilianokokoro.umihi.music.core.domain.TrackKind
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * Anonymous YouTube Music browsing through NewPipe.
 *
 * The InnerTube client next door reaches richer pages but parses into Android models today; this
 * path returns the neutral types from :music-core, which is what a non-Android client needs.
 * Signed-in surfaces (library, liked songs) stay out of reach here by design — they need the
 * account cookies.
 */
object NewPipeMusicSearch {

    suspend fun search(
        query: String,
        filter: CatalogSearchFilter = CatalogSearchFilter.SONGS,
        limit: Int = 30,
    ): List<CatalogSearchItem> = withContext(Dispatchers.IO) {
        YoutubeExtractor.ensureInitialized()

        val extractor = ServiceList.YouTube.getSearchExtractor(query, listOf(filter.token()), "")
        extractor.fetchPage()

        extractor.initialPage.items
            .take(limit)
            .mapNotNull { it.toSearchItem() }
            .map { item ->
                if (filter == CatalogSearchFilter.VIDEOS && item is CatalogSearchItem.TrackItem)
                    item.copy(track = item.track.copy(kind = TrackKind.VIDEO)) else item
            }
    }

    /** An album is a YouTube Music playlist; [id] is the playlist URL NewPipe handed back. */
    suspend fun album(id: String): CatalogAlbumPage = withContext(Dispatchers.IO) {
        YoutubeExtractor.ensureInitialized()

        val extractor = ServiceList.YouTube.getPlaylistExtractor(id)
        extractor.fetchPage()

        // NewPipe throws rather than returning null when a YouTube Music album playlist omits a
        // header field, so every optional one is read defensively: a missing artist name must not
        // cost the user the track list.
        val artistName = runCatching { extractor.uploaderName.orEmpty() }.getOrDefault("")
        val albumName = runCatching { extractor.name.orEmpty() }.getOrDefault("")
        val items = mutableListOf<StreamInfoItem>()
        var page = extractor.initialPage
        var pageCount = 0
        while (true) {
            coroutineContext.ensureActive()
            items += page.items.filterIsInstance<StreamInfoItem>()
            if (!page.hasNextPage()) break
            check(++pageCount <= 200) { "Collection trop volumineuse" }
            page = extractor.getPage(page.nextPage)
        }
        val tracks = items
            .mapIndexed { index, item ->
                item.toTrack().copy(
                    positionInCollection = index + 1,
                    album = AlbumRef(id = id, name = albumName),
                )
            }

        CatalogAlbumPage(
            title = runCatching { extractor.name.orEmpty() }.getOrDefault(""),
            tracks = tracks,
            artworkUrl = runCatching { extractor.thumbnails.largest()?.url.orEmpty() }.getOrDefault(""),
            artistName = artistName,
            artistId = runCatching { extractor.uploaderUrl }.getOrNull(),
            subtitle = artistName,
            secondSubtitle = "${tracks.size} titres",
            description = runCatching { extractor.description?.content.orEmpty() }.getOrDefault(""),
            shelves = emptyList(),
            // NewPipe exposes neither the library-save endpoint's collection id nor its state.
            audioPlaylistId = null,
            savedToLibrary = null,
        )
    }

    /** [id] is the channel URL NewPipe handed back for the artist. */
    suspend fun artist(id: String): CatalogArtistPage = withContext(Dispatchers.IO) {
        YoutubeExtractor.ensureInitialized()

        val extractor = ServiceList.YouTube.getChannelExtractor(id)
        extractor.fetchPage()

        val tabs = runCatching { extractor.tabs }.getOrDefault(emptyList())
        val tracks = tabs.firstNotNullOfOrNull { tab ->
            runCatching {
                ServiceList.YouTube.getChannelTabExtractor(tab).apply { fetchPage() }
                    .initialPage.items.filterIsInstance<StreamInfoItem>()
            }.getOrNull()?.takeIf { it.isNotEmpty() }
        }.orEmpty().map { it.toTrack() }

        CatalogArtistPage(
            name = runCatching { extractor.name.orEmpty() }.getOrDefault(""),
            artworkUrl = runCatching { extractor.avatars.largest()?.url.orEmpty() }.getOrDefault(""),
            channelId = id,
            description = runCatching { extractor.description }.getOrNull(),
            followerCountLabel = null,
            subscriptionChannelId = null,
            // Following needs the account cookies, which this anonymous path never carries.
            subscribed = null,
            topTracks = tracks,
            allTracks = tracks,
            shelves = emptyList(),
        )
    }
}

private fun CatalogSearchFilter.token(): String = when (this) {
    CatalogSearchFilter.SONGS, CatalogSearchFilter.ALL -> "music_songs"
    CatalogSearchFilter.VIDEOS -> "music_videos"
    CatalogSearchFilter.ALBUMS -> "music_albums"
    CatalogSearchFilter.ARTISTS -> "music_artists"
}

private fun InfoItem.toSearchItem(): CatalogSearchItem? = when (this) {
    is StreamInfoItem -> CatalogSearchItem.TrackItem(toTrack())
    is PlaylistInfoItem -> CatalogSearchItem.Album(
        id = url,
        title = name.orEmpty(),
        subtitle = uploaderName.orEmpty(),
        artworkUrl = thumbnails.largest()?.url.orEmpty(),
    )

    is ChannelInfoItem -> CatalogSearchItem.Artist(
        id = url,
        name = name.orEmpty(),
        artworkUrl = thumbnails.largest()?.url.orEmpty(),
    )

    else -> null
}

internal fun StreamInfoItem.toTrack(): Track {
    val videoId = videoIdOf(url)
    return Track(
        id = videoId ?: url,
        title = name.orEmpty(),
        artist = ArtistRef(id = uploaderUrl, name = uploaderName.orEmpty()),
        // NewPipe reports seconds, and -1 when the item carries no duration at all.
        durationMs = duration.takeIf { it > 0L }?.times(1_000L),
        artworkUrl = thumbnails.largest()?.url,
        kind = TrackKind.SONG,
        sourceUri = url,
    )
}

private fun List<Image>.largest(): Image? = maxByOrNull { it.height }

/** Pulls the `v=` id out of a watch URL; NewPipe returns full URLs, [Track.id] holds bare ids. */
internal fun videoIdOf(url: String?): String? {
    if (url.isNullOrBlank()) return null
    return runCatching {
        val uri = URI(url)
        if (uri.scheme !in setOf("https", "http")) return null
        val host = uri.host?.lowercase() ?: return null
        val id = when (host) {
            "youtu.be" -> uri.path.trim('/').takeIf { '/' !in it }
            "youtube.com", "www.youtube.com", "music.youtube.com", "m.youtube.com" -> {
                if (uri.path == "/watch") uri.rawQuery.orEmpty().split('&').firstNotNullOfOrNull { pair ->
                    val parts = pair.split('=', limit = 2)
                    if (parts.size == 2 && parts[0] == "v") URLDecoder.decode(parts[1], StandardCharsets.UTF_8) else null
                } else uri.path.split('/').takeIf { it.size == 3 && it[1] in setOf("shorts", "embed", "v") }?.get(2)
            }
            else -> null
        }
        id?.takeIf { Regex("[A-Za-z0-9_-]{11}").matches(it) }
    }.getOrNull()
}

/** Accept only provider playlist links; the caller can then open the collection instead of searching its URL. */
fun youtubePlaylistUrl(input: String): String? = runCatching {
    val uri = URI(input.trim())
    if (uri.scheme !in setOf("http", "https") || uri.host?.lowercase() !in setOf("youtube.com", "www.youtube.com", "music.youtube.com", "m.youtube.com")) return null
    val id = uri.rawQuery.orEmpty().split('&').firstNotNullOfOrNull {
        val pair = it.split('=', limit = 2)
        if (pair.size == 2 && pair[0] == "list") pair[1] else null
    } ?: return null
    if (!Regex("[A-Za-z0-9_-]{2,150}").matches(id)) return null
    "https://www.youtube.com/playlist?list=$id"
}.getOrNull()
