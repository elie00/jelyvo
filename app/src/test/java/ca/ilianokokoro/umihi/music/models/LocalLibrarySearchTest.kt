package ca.ilianokokoro.umihi.music.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La recherche locale de l'onglet « Ma bibliotheque » : elle doit rester insensible aux accents,
 * regrouper par origine dans un ordre stable, omettre les sections vides et dedoublonner PAR
 * section (un titre like ET telecharge doit apparaitre dans les deux, c'est le regroupement par
 * origine qui fait sens pour l'utilisateur).
 */
class LocalLibrarySearchTest {

    private fun song(id: String, title: String, artist: String = "") =
        Song(youtubeId = id, title = title, artist = artist)

    @Test
    fun `a blank query yields no section`() {
        val sections = searchLocalLibrary(
            query = "  ",
            likedSongs = listOf(song("a", "Melodie")),
            localPlaylists = emptyList(),
            localAudioSongs = emptyList(),
            downloadedSongs = emptyList(),
        )

        assertEquals(emptyList<LocalLibrarySection>(), sections)
    }

    @Test
    fun `matching is accent and case insensitive on title and artist`() {
        val sections = searchLocalLibrary(
            query = "melodie",
            likedSongs = listOf(
                song("a", "Mélodie du bonheur"),
                song("b", "Autre chose", artist = "MÉLODIE"),
                song("c", "Sans rapport"),
            ),
            localPlaylists = emptyList(),
            localAudioSongs = emptyList(),
            downloadedSongs = emptyList(),
        )

        assertEquals(1, sections.size)
        assertEquals(LocalLibrarySectionKind.LIKED, sections.first().kind)
        assertEquals(listOf("a", "b"), sections.first().songs.map { it.youtubeId })
    }

    @Test
    fun `empty sections are omitted and order follows the origin enum`() {
        val sections = searchLocalLibrary(
            query = "hit",
            likedSongs = listOf(song("liked", "Hit like")),
            localPlaylists = emptyList(),
            localAudioSongs = emptyList(),
            downloadedSongs = listOf(song("dl", "Hit telecharge")),
        )

        assertEquals(
            listOf(LocalLibrarySectionKind.LIKED, LocalLibrarySectionKind.DOWNLOADS),
            sections.map { it.kind },
        )
    }

    @Test
    fun `playlist section matches names and track titles`() {
        val sections = searchLocalLibrary(
            query = "route",
            likedSongs = emptyList(),
            localPlaylists = listOf(
                LocalPlaylistSearchSource(
                    id = "p1",
                    name = "Sur la ROUTE",
                    songs = listOf(song("x", "Sans rapport")),
                ),
                LocalPlaylistSearchSource(
                    id = "p2",
                    name = "Calme",
                    songs = listOf(song("y", "La route est longue")),
                ),
            ),
            localAudioSongs = emptyList(),
            downloadedSongs = emptyList(),
        )

        assertEquals(1, sections.size)
        val playlists = sections.first()
        assertEquals(LocalLibrarySectionKind.PLAYLISTS, playlists.kind)
        assertEquals(listOf("p1"), playlists.playlists.map { it.id })
        assertEquals(1, playlists.playlists.first().songCount)
        assertEquals(listOf("y"), playlists.songs.map { it.youtubeId })
    }

    @Test
    fun `duplicates are removed within a section but kept across sections`() {
        val duplicated = song("dup", "Meme titre")
        val sections = searchLocalLibrary(
            query = "meme",
            likedSongs = listOf(duplicated, duplicated.copy(uid = "other")),
            localPlaylists = emptyList(),
            localAudioSongs = emptyList(),
            downloadedSongs = listOf(duplicated),
        )

        assertEquals(2, sections.size)
        assertEquals(listOf("dup"), sections[0].songs.map { it.youtubeId })
        assertEquals(listOf("dup"), sections[1].songs.map { it.youtubeId })
    }

    @Test
    fun `a track present in several playlists appears once`() {
        val shared = song("s", "Refrain partage")
        val sections = searchLocalLibrary(
            query = "refrain",
            likedSongs = emptyList(),
            localPlaylists = listOf(
                LocalPlaylistSearchSource(id = "p1", name = "Une", songs = listOf(shared)),
                LocalPlaylistSearchSource(id = "p2", name = "Deux", songs = listOf(shared)),
            ),
            localAudioSongs = emptyList(),
            downloadedSongs = emptyList(),
        )

        assertEquals(1, sections.size)
        assertEquals(listOf("s"), sections.first().songs.map { it.youtubeId })
        assertTrue(sections.first().playlists.isEmpty())
    }
}
