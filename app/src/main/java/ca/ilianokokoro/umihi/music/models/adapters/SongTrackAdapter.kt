package ca.ilianokokoro.umihi.music.models.adapters

import ca.ilianokokoro.umihi.music.core.domain.AlbumRef
import ca.ilianokokoro.umihi.music.core.domain.ArtistRef
import ca.ilianokokoro.umihi.music.core.domain.Track
import ca.ilianokokoro.umihi.music.core.domain.TrackKind
import ca.ilianokokoro.umihi.music.core.domain.TrackSource
import ca.ilianokokoro.umihi.music.core.domain.formatTrackDuration
import ca.ilianokokoro.umihi.music.core.domain.parseTrackDurationMillis
import ca.ilianokokoro.umihi.music.models.Song

/** Android boundary: strips Room/Media3 concerns from a legacy [Song]. */
fun Song.toTrack(): Track = Track(
    id = youtubeId,
    title = title,
    artist = ArtistRef(id = artistId, name = artist),
    album = albumId?.let { id -> AlbumRef(id = id, name = albumName.orEmpty()) },
    durationMs = parseTrackDurationMillis(duration),
    artworkUrl = (thumbnailPath ?: thumbnailHref).takeIf(String::isNotBlank),
    explicit = isExplicit,
    liked = isLiked,
    source = if (isLocal) TrackSource.LOCAL_DEVICE else TrackSource.YOUTUBE,
    sourceUri = localUri.takeIf { isLocal },
    kind = if (isVideo) TrackKind.VIDEO else TrackKind.SONG,
    positionInCollection = trackNumber,
    playCountLabel = playCount,
)

/**
 * Temporary Android adapter used while screens, persistence and Media3 still consume [Song].
 */
fun Track.toSong(): Song = Song(
    youtubeId = id,
    title = title,
    artist = artist.name,
    duration = formatTrackDuration(durationMs),
    thumbnailHref = artworkUrl.orEmpty(),
    isExplicit = explicit,
    isLiked = liked,
).also { song ->
    song.artistId = artist.id
    song.albumId = album?.id
    song.albumName = album?.name
    song.isLocal = source == TrackSource.LOCAL_DEVICE
    song.localUri = sourceUri.takeIf { song.isLocal }
    song.isVideo = kind == TrackKind.VIDEO
    song.trackNumber = positionInCollection
    song.playCount = playCountLabel
}

