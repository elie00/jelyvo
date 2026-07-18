package ca.ilianokokoro.umihi.music.models

import androidx.compose.runtime.Immutable

@Immutable
data class Artist(
    val name: String,
    val thumbnailHref: String = "",
    val channelUrl: String,
)

@Immutable
data class ArtistPage(
    val artist: Artist,
    val songs: List<Song> = emptyList(),
)
