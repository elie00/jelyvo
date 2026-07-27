package ca.ilianokokoro.umihi.music.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Le decoupage par disque d'un album multi-CD : YouTube n'expose pas de champ, il est deduit des
 * shelves ou d'une remise a zero de la numerotation des pistes. La fonction de regroupement est ce
 * que l'ecran album affiche, donc un album simple doit continuer a sortir un seul groupe — sinon
 * un en-tete "Disque 1" apparaitrait sur tous les albums.
 */
class AlbumTrackGroupingTest {

    private fun song(id: String, trackNumber: Int? = null, discNumber: Int? = null) =
        Song(youtubeId = id, title = id).also {
            it.trackNumber = trackNumber
            it.discNumber = discNumber
        }

    @Test
    fun `an album without disc numbers stays a single group`() {
        val songs = listOf(song("a"), song("b"), song("c"))

        val discs = groupSongsByDisc(songs)

        assertEquals(1, discs.size)
        assertEquals(1, discs.first().number)
        assertEquals(songs, discs.first().songs)
    }

    @Test
    fun `songs are grouped per disc in order`() {
        val songs = listOf(
            song("a", discNumber = 1),
            song("b", discNumber = 1),
            song("c", discNumber = 2),
        )

        val discs = groupSongsByDisc(songs)

        assertEquals(listOf(1, 2), discs.map { it.number })
        assertEquals(listOf("a", "b"), discs[0].songs.map { it.youtubeId })
        assertEquals(listOf("c"), discs[1].songs.map { it.youtubeId })
    }

    @Test
    fun `an empty album yields no group at all`() {
        assertEquals(emptyList<AlbumDisc>(), groupSongsByDisc(emptyList()))
    }

    @Test
    fun `a track number restart opens a second disc`() {
        val songs = listOf(
            song("a", trackNumber = 1),
            song("b", trackNumber = 2),
            song("c", trackNumber = 1),
            song("d", trackNumber = 2),
        )

        inferDiscNumbers(songs)

        assertEquals(listOf(1, 1, 2, 2), songs.map { it.discNumber })
        assertEquals(listOf(2, 2), groupSongsByDisc(songs).map { it.songs.size })
    }

    /**
     * Limite assumee : un double album numerote en continu 1..N ne presente aucune rupture. Rien
     * n'est invente, la liste reste a plat.
     */
    @Test
    fun `a continuously numbered album is left flat`() {
        val songs = listOf(song("a", trackNumber = 1), song("b", trackNumber = 2))

        inferDiscNumbers(songs)

        songs.forEach { assertNull(it.discNumber) }
        assertEquals(1, groupSongsByDisc(songs).size)
    }

    @Test
    fun `songs without any track number are left untouched`() {
        val songs = listOf(song("a"), song("b"))

        inferDiscNumbers(songs)

        songs.forEach { assertNull(it.discNumber) }
    }

    /** Un decoupage venant des shelves fait autorite : l'inference ne doit pas l'ecraser. */
    @Test
    fun `disc numbers coming from the shelves are kept`() {
        val songs = listOf(
            song("a", trackNumber = 1, discNumber = 1),
            song("b", trackNumber = 2, discNumber = 2),
        )

        inferDiscNumbers(songs)

        assertEquals(listOf(1, 2), songs.map { it.discNumber })
    }
}
