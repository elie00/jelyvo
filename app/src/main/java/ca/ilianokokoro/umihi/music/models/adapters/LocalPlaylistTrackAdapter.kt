package ca.ilianokokoro.umihi.music.models.adapters

import ca.ilianokokoro.umihi.music.core.domain.ArtistRef
import ca.ilianokokoro.umihi.music.core.domain.Track
import ca.ilianokokoro.umihi.music.core.domain.formatTrackDuration
import ca.ilianokokoro.umihi.music.core.domain.parseTrackDurationMillis
import ca.ilianokokoro.umihi.music.models.LocalPlaylistSong

/** Persistence boundary from an on-device playlist row to the shared track model. */
fun LocalPlaylistSong.toTrack(): Track = Track(
    id = id,
    title = title,
    artist = ArtistRef(name = artist),
    durationMs = parseTrackDurationMillis(duration),
    artworkUrl = thumbnailHref.takeIf(String::isNotBlank),
)

/**
 * Creates a fresh playlist row from a shared track. The row intentionally receives a new `uid`:
 * playlist-row identity is local UI state and is not part of the musical identity in [Track].
 */
fun Track.toLocalPlaylistSong(): LocalPlaylistSong = LocalPlaylistSong(
    id = id,
    title = title,
    artist = artist.name,
    thumbnailHref = artworkUrl.orEmpty(),
    duration = formatTrackDuration(durationMs),
)

