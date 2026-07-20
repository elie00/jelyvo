package ca.ilianokokoro.umihi.music.models

import androidx.compose.runtime.Immutable

@Immutable
data class AlbumPage(
    val name: String,
    val songs: List<Song> = emptyList(),
    val cover: String = "",
    /** Artist line of the header (may be empty on older header shapes). */
    val artist: String = "",
    /** YouTube Music artist browseId ("UC…"), when the header links one. */
    val artistId: String? = null,
    /** Album type + release year as YouTube formats it, e.g. "Album • 2023". */
    val subtitle: String = "",
    /** Track count + total duration as YouTube formats it, e.g. "12 songs • 41 minutes". */
    val secondSubtitle: String = "",
    /** Editorial description of the album, when the browse response provides one. */
    val description: String = "",
    /** Album carousels below the tracks (e.g. "Other versions"). */
    val sections: List<ArtistSection> = emptyList(),
)

/** Metadata parsed from the header of an album browse response. Missing fields are empty. */
@Immutable
data class AlbumHeaderInfo(
    val title: String = "",
    val artist: String = "",
    val artistId: String? = null,
    val subtitle: String = "",
    val secondSubtitle: String = "",
    val description: String = "",
)
