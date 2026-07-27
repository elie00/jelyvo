package ca.ilianokokoro.umihi.music.models

import androidx.compose.runtime.Immutable

/**
 * Un disque d'un album multi-CD : son numero et les pistes qu'il contient, dans l'ordre de la
 * reponse. Un album simple donne une seule entree, ce qui permet a l'ecran de toujours parcourir
 * la meme structure.
 */
@Immutable
data class AlbumDisc(
    val number: Int,
    val songs: List<Song>,
)

/**
 * Regroupe les pistes d'un album par disque, en conservant l'ordre d'origine.
 *
 * Fonction pure (testee par AlbumTrackGroupingTest) : l'ecran se contente d'afficher les groupes et
 * de numeroter les pistes a partir de 1 dans chacun d'eux, comme Spotify. Les pistes sans
 * [Song.discNumber] tombent sur le disque 1, et une liste entierement non renseignee donne un seul
 * groupe — donc aucun en-tete affiche, l'affichage a plat d'avant.
 *
 * Le regroupement est CONSECUTIF et non par cle : l'ordre du disque prime, un numero qui
 * reapparaitrait plus loin ouvre un nouveau groupe plutot que de remonter les pistes.
 */
fun groupSongsByDisc(songs: List<Song>): List<AlbumDisc> {
    if (songs.isEmpty()) {
        return emptyList()
    }

    val discs = mutableListOf<AlbumDisc>()
    var currentNumber = songs.first().discNumber ?: 1
    var current = mutableListOf<Song>()

    for (song in songs) {
        val number = song.discNumber ?: 1
        if (number != currentNumber && current.isNotEmpty()) {
            discs.add(AlbumDisc(currentNumber, current))
            current = mutableListOf()
        }
        currentNumber = number
        current.add(song)
    }
    discs.add(AlbumDisc(currentNumber, current))

    return discs
}

/**
 * Deduit [Song.discNumber] d'une remise a zero de la numerotation des pistes : un album double dont
 * les pistes repartent a 1 signale ainsi le second disque.
 *
 * C'est le SEUL indice disponible quand la reponse ne separe pas les disques en plusieurs shelves.
 * Limite intrinseque assumee : si YouTube numerote en continu 1..N sur un double album, aucune
 * rupture n'apparait et la liste reste a plat — on ne fabrique pas de disque au jugé. De meme, une
 * liste sans aucun [Song.trackNumber] ressort inchangee.
 *
 * Les pistes sont modifiees en place (comme les autres champs transitoires de [Song]) et la liste
 * d'origine est renvoyee pour permettre le chainage.
 */
fun inferDiscNumbers(songs: List<Song>): List<Song> {
    // Un decoupage deja fourni par la source (shelves) fait autorite : on n'y touche pas.
    if (songs.any { it.discNumber != null }) {
        return songs
    }

    var disc = 1
    var previousTrackNumber: Int? = null

    for (song in songs) {
        val trackNumber = song.trackNumber
        if (trackNumber != null && previousTrackNumber != null &&
            trackNumber <= previousTrackNumber
        ) {
            disc++
        }
        if (trackNumber != null) {
            previousTrackNumber = trackNumber
        }
        song.discNumber = disc
    }

    // Aucune rupture detectee : on laisse le champ vide plutot que de marquer tout le monde
    // "disque 1", pour que l'absence de donnee reste distinguable d'un vrai album simple.
    if (disc == 1) {
        songs.forEach { it.discNumber = null }
    }

    return songs
}
