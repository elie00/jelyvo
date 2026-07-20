package ca.ilianokokoro.umihi.music.models

/** The connected account's collection: liked songs, saved albums and followed artists. */
data class Library(
    val likedSongs: List<Song> = emptyList(),
    val savedAlbums: List<SearchItem.Album> = emptyList(),
    val followedArtists: List<SearchItem.Artist> = emptyList(),
) {
    val isEmpty: Boolean
        get() = likedSongs.isEmpty() && savedAlbums.isEmpty() && followedArtists.isEmpty()
}
