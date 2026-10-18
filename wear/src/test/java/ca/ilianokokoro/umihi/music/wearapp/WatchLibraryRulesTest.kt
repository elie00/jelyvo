package ca.ilianokokoro.umihi.music.wearapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchLibraryRulesTest {
    private fun song(id: Char, title: String = "Titre $id") =
        WatchSong(videoId = "$id".repeat(11), title = title, artist = "Artiste", artworkUrl = "art")

    @Test fun `toggling adds a favorite first and toggling again removes it`() {
        val existing = listOf(song('a'), song('b'))

        val (added, wasAdded) = toggledFavorite(existing, song('c'), max = 100)
        val (removed, wasAddedAgain) = toggledFavorite(added, song('c'), max = 100)

        assertTrue(wasAdded)
        assertEquals(listOf(song('c'), song('a'), song('b')), added)
        assertFalse(wasAddedAgain)
        assertEquals(existing, removed)
    }

    @Test fun `favorites are capped by dropping the oldest`() {
        val (songs, _) = toggledFavorite(listOf(song('a'), song('b')), song('c'), max = 2)

        assertEquals(listOf(song('c'), song('a')), songs)
    }

    @Test fun `replaying a recent moves it to the front with fresh metadata`() {
        val recents = listOf(song('a'), song('b'), song('c'))

        val updated = withRecent(recents, song('b', title = "Nouveau titre"), max = 30)

        assertEquals(listOf("Nouveau titre", "Titre a", "Titre c"), updated.map(WatchSong::title))
    }

    @Test fun `history keeps only the most recent entries`() {
        val updated = withRecent(listOf(song('a'), song('b')), song('c'), max = 2)

        assertEquals(listOf(song('c'), song('a')), updated)
    }

    @Test fun `stored songs survive an encode and decode round trip`() {
        val songs = listOf(
            song('a').copy(durationText = "3:41", isExplicit = true),
            song('b').copy(title = "Guillemets \" et accents é", artworkUrl = ""),
        )

        assertEquals(songs, decodeWatchSongs(encodeWatchSongs(songs)))
    }

    @Test fun `unreadable storage decodes as an empty library`() {
        assertEquals(emptyList<WatchSong>(), decodeWatchSongs(null))
        assertEquals(emptyList<WatchSong>(), decodeWatchSongs("{ pas du json"))
        assertEquals(emptyList<WatchSong>(), decodeWatchSongs("""{"videoId":"x"}"""))
    }

    @Test fun `entries without an id are skipped and missing fields default`() {
        val decoded = decodeWatchSongs("""[{"title":"sans id"},{"videoId":"aaaaaaaaaaa"}]""")

        assertEquals(listOf(WatchSong("aaaaaaaaaaa", "", "", "")), decoded)
    }
}
