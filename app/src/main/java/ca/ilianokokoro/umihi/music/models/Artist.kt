package ca.ilianokokoro.umihi.music.models

import androidx.compose.runtime.Immutable

@Immutable
data class Artist(
    val name: String,
    val thumbnailHref: String = "",
    val channelUrl: String,
)

@Immutable
data class ArtistAlbum(
    val browseId: String,
    val title: String,
    val thumbnail: String = "",
)

/** A carousel section of an artist page (e.g. "Albums", "Singles", "Videos"). */
@Immutable
data class ArtistSection(
    val title: String,
    val albums: List<ArtistAlbum> = emptyList(),
    val videos: List<Song> = emptyList(),
)

@Immutable
data class ArtistPage(
    val artist: Artist,
    val songs: List<Song> = emptyList(),
    val sections: List<ArtistSection> = emptyList(),
)
