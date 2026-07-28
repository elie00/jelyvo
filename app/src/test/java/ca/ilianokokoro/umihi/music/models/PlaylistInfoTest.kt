package ca.ilianokokoro.umihi.music.models

import ca.ilianokokoro.umihi.music.core.Constants
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistInfoTest {

    @Test
    fun `all known liked playlist ids are recognized and protected from editing`() {
        listOf(
            "LM",
            Constants.YoutubeApi.Browse.LIKED_SONGS_BROWSE_ID,
            "liked",
        ).forEach { id ->
            val info = PlaylistInfo(id = id)
            assertTrue(info.isLikedSongsPlaylist)
            assertFalse(info.isEditable)
        }
    }

    @Test
    fun `ordinary playlists stay editable`() {
        val info = PlaylistInfo(id = "VLPL_user_playlist")

        assertFalse(info.isLikedSongsPlaylist)
        assertTrue(info.isEditable)
    }
}
