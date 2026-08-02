package ca.ilianokokoro.umihi.music.models

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `playlist snapshot survives a disk cache round trip`() {
        val original = Playlist(
            info = PlaylistInfo(id = "VLLM", title = "J'aime"),
            songs = listOf(
                Song(
                    youtubeId = "abcdefghijk",
                    title = "Titre conservé",
                    artist = "Artiste",
                    thumbnailHref = "https://example.test/cover.jpg",
                    isLiked = true,
                )
            ),
        )

        val restored = json.decodeFromString<Playlist>(json.encodeToString(original))

        assertEquals(original.info, restored.info)
        assertEquals(original.songs, restored.songs)
    }
}
