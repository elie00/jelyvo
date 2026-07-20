package ca.ilianokokoro.umihi.music.models

import androidx.compose.runtime.Immutable

@Immutable
data class HomeFeedSection(
    val title: String,
    val items: List<HomeFeedItem>,
)

@Immutable
sealed interface HomeFeedItem {
    data class Track(val song: Song) : HomeFeedItem

    data class Playlist(
        val playlist: PlaylistInfo,
        val subtitle: String = "",
    ) : HomeFeedItem

    data class Album(
        val browseId: String,
        val title: String,
        val subtitle: String,
        val thumbnail: String,
    ) : HomeFeedItem

    data class Artist(
        val browseId: String,
        val name: String,
        val thumbnail: String,
    ) : HomeFeedItem
}
