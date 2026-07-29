package ca.ilianokokoro.umihi.music.models

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/** The connected account's collection: liked songs, saved albums and followed artists. */
@Serializable
@Immutable
data class Library(
    val likedSongs: List<Song> = emptyList(),
    val savedAlbums: List<SearchItem.Album> = emptyList(),
    val followedArtists: List<SearchItem.Artist> = emptyList(),
) {
    val isEmpty: Boolean
        get() = likedSongs.isEmpty() && savedAlbums.isEmpty() && followedArtists.isEmpty()
}
