package ca.ilianokokoro.umihi.music.ui.screens.stats

import ca.ilianokokoro.umihi.music.models.ArtistListeningStat
import ca.ilianokokoro.umihi.music.models.TrackListeningStat

data class ListeningStatsState(
    val topTracks: List<TrackListeningStat> = emptyList(),
    val topArtists: List<ArtistListeningStat> = emptyList(),
)
