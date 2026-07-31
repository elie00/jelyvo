package ca.ilianokokoro.umihi.music.models.adapters

import ca.ilianokokoro.umihi.music.core.domain.ArtistRef
import ca.ilianokokoro.umihi.music.core.domain.Track
import ca.ilianokokoro.umihi.music.core.domain.formatTrackDuration
import ca.ilianokokoro.umihi.music.core.domain.parseTrackDurationMillis
import ca.ilianokokoro.umihi.music.models.PlayHistoryEntry

/** Persistence boundary from a local play-history row to the shared track model. */
fun PlayHistoryEntry.toTrack(): Track = Track(
    id = youtubeId,
    title = title,
    artist = ArtistRef(name = artist),
    durationMs = parseTrackDurationMillis(duration),
    artworkUrl = thumbnailHref.takeIf(String::isNotBlank),
    explicit = isExplicit,
)

/**
 * Creates a fresh history row for one play of this track at [playedAt]. The row id is a local
 * auto-generated key and, like resume positions, is not part of the musical identity in [Track].
 */
fun Track.toPlayHistoryEntry(playedAt: Long): PlayHistoryEntry = PlayHistoryEntry(
    youtubeId = id,
    title = title,
    artist = artist.name,
    thumbnailHref = artworkUrl.orEmpty(),
    duration = formatTrackDuration(durationMs),
    isExplicit = explicit,
    playedAt = playedAt,
)
