package ca.ilianokokoro.umihi.music.models

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
@Immutable
data class HomeFeedSection(
    val title: String,
    val items: List<HomeFeedItem>,
)

@Serializable
@Immutable
sealed interface HomeFeedItem {
    @Serializable
    data class Track(val song: Song) : HomeFeedItem

    @Serializable
    data class Playlist(
        val playlist: PlaylistInfo,
        val subtitle: String = "",
    ) : HomeFeedItem

    @Serializable
    data class Album(
        val browseId: String,
        val title: String,
        val subtitle: String,
        val thumbnail: String,
    ) : HomeFeedItem

    @Serializable
    data class Artist(
        val browseId: String,
        val name: String,
        val thumbnail: String,
    ) : HomeFeedItem
}
