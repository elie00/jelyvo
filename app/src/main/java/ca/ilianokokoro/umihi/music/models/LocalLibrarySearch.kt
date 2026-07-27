package ca.ilianokokoro.umihi.music.models

import androidx.compose.runtime.Immutable
import ca.ilianokokoro.umihi.music.extensions.containsNormalized

/**
 * Une playlist locale aplatie pour la recherche : son nom affiche et ses titres deja decodes
 * (le JSON n'est decode qu'une fois, en amont, par le ViewModel).
 */
data class LocalPlaylistSearchSource(
    val id: String,
    val name: String,
    val coverHref: String = "",
    val songs: List<Song> = emptyList(),
)

/** Une playlist locale dont le NOM correspond a la requete. */
@Immutable
data class LocalPlaylistNameMatch(
    val id: String,
    val name: String,
    val coverHref: String,
    val songCount: Int,
)

/** L'origine d'une section de resultats locaux, dans l'ordre d'affichage. */
enum class LocalLibrarySectionKind { LIKED, PLAYLISTS, LOCAL_AUDIO, DOWNLOADS }

/**
 * Une section de l'onglet « Ma bibliotheque » : les correspondances d'une origine donnee, avec
 * les playlists (nom correspondant) avant les titres. Seule la section PLAYLISTS peut porter des
 * [playlists] ; les autres n'ont que des [songs].
 */
@Immutable
data class LocalLibrarySection(
    val kind: LocalLibrarySectionKind,
    val playlists: List<LocalPlaylistNameMatch> = emptyList(),
    val songs: List<Song> = emptyList(),
)

/**
 * Recherche purement locale (aucun reseau) dans la bibliotheque : titres likes, playlists locales
 * (noms ET titres), audio du telephone et telechargements.
 *
 * Fonction pure (testee par LocalLibrarySearchTest) : la correspondance est insensible aux accents
 * et a la casse via [containsNormalized], sur le titre ou l'artiste. Chaque origine devient une
 * section (omise quand vide) et les doublons sont supprimes PAR section — un titre a la fois like
 * et telecharge apparait dans les deux sections, c'est voulu (regroupement par origine).
 */
fun searchLocalLibrary(
    query: String,
    likedSongs: List<Song>,
    localPlaylists: List<LocalPlaylistSearchSource>,
    localAudioSongs: List<Song>,
    downloadedSongs: List<Song>,
): List<LocalLibrarySection> {
    if (query.isBlank()) {
        return emptyList()
    }

    fun List<Song>.matching(): List<Song> = filter { song ->
        song.title.containsNormalized(query) || song.artist.containsNormalized(query)
    }.distinctBy { it.youtubeId }

    val sections = mutableListOf<LocalLibrarySection>()

    likedSongs.matching().takeIf { it.isNotEmpty() }?.let { songs ->
        sections += LocalLibrarySection(LocalLibrarySectionKind.LIKED, songs = songs)
    }

    val playlistNameMatches = localPlaylists
        .filter { it.name.containsNormalized(query) }
        .map { LocalPlaylistNameMatch(it.id, it.name, it.coverHref, it.songs.size) }
    val playlistTrackMatches = localPlaylists.flatMap { it.songs }.matching()
    if (playlistNameMatches.isNotEmpty() || playlistTrackMatches.isNotEmpty()) {
        sections += LocalLibrarySection(
            kind = LocalLibrarySectionKind.PLAYLISTS,
            playlists = playlistNameMatches,
            songs = playlistTrackMatches,
        )
    }

    localAudioSongs.matching().takeIf { it.isNotEmpty() }?.let { songs ->
        sections += LocalLibrarySection(LocalLibrarySectionKind.LOCAL_AUDIO, songs = songs)
    }

    downloadedSongs.matching().takeIf { it.isNotEmpty() }?.let { songs ->
        sections += LocalLibrarySection(LocalLibrarySectionKind.DOWNLOADS, songs = songs)
    }

    return sections
}
