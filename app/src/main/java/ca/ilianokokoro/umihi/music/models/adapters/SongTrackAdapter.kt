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
    artworkUrl = thumbnailHref.takeIf(String::isNotBlank),
    offlineArtworkPath = thumbnailPath,
    offlineAudioPath = audioFilePath,
    explicit = isExplicit,
    liked = isLiked,
    source = if (isLocal) TrackSource.LOCAL_DEVICE else TrackSource.YOUTUBE,
    sourceUri = localUri.takeIf { isLocal },
    kind = if (isVideo) TrackKind.VIDEO else TrackKind.SONG,
    preferVideoStream = forceVideo,
    positionInCollection = trackNumber,
    discNumber = discNumber,
    viewCountLabel = viewCount,
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
    thumbnailPath = offlineArtworkPath,
    audioFilePath = offlineAudioPath,
    isExplicit = explicit,
    isLiked = liked,
).also { song ->
    song.artistId = artist.id
    song.albumId = album?.id
    song.albumName = album?.name
    song.isLocal = source == TrackSource.LOCAL_DEVICE
    song.localUri = sourceUri.takeIf { song.isLocal }
    song.isVideo = kind == TrackKind.VIDEO
    song.forceVideo = preferVideoStream
    song.trackNumber = positionInCollection
    song.discNumber = discNumber
    song.viewCount = viewCountLabel
    song.playCount = playCountLabel
}

