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
    /** Card subtitle, e.g. "Album • 2025" — used to infer the release year. */
    val subtitle: String = "",
) {
    /** The release year parsed from the subtitle, or null when absent. */
    val year: Int?
        get() = Regex("""\b(19|20)\d{2}\b""").find(subtitle)?.value?.toIntOrNull()
}

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
    /** Every song reachable from the page, i.e. the Songs shelf followed through "Show all". */
    val songs: List<Song> = emptyList(),
    /**
     * The Songs shelf as YouTube Music first renders it: the artist's most-streamed tracks, in the
     * order the API returns them, with their play counts. Kept separate from [songs] because
     * following "Show all" replaces that ranking with the full, differently ordered discography.
     */
    val topSongs: List<Song> = emptyList(),
    val sections: List<ArtistSection> = emptyList(),
)
