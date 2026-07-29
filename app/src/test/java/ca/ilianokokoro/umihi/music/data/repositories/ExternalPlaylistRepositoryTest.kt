package ca.ilianokokoro.umihi.music.data.repositories

import ca.ilianokokoro.umihi.music.models.importing.MusicImportService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalPlaylistRepositoryTest {
    private val repository = ExternalPlaylistRepository()

    @Test
    fun `shared text exposes and cleans its first playlist URL`() {
        assertEquals(
            "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M",
            extractSharedPlaylistUrl(
                "Écoute ma playlist https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M)."
            ),
        )
    }

    @Test
    fun `supported service detection uses the URL host`() {
        assertEquals(
            MusicImportService.SPOTIFY,
            detectMusicImportService("https://open.spotify.com/playlist/abc"),
        )
        assertEquals(
            MusicImportService.APPLE_MUSIC,
            detectMusicImportService("https://music.apple.com/fr/playlist/test/pl.123"),
        )
        assertEquals(
            MusicImportService.DEEZER,
            detectMusicImportService("https://www.deezer.com/fr/playlist/123"),
        )
        assertNull(detectMusicImportService("https://spotify.example.com/playlist/abc"))
    }

    @Test
    fun `Spotify embed metadata becomes an import playlist`() {
        val html = """
            <html><script id="__NEXT_DATA__" type="application/json">
            {
              "props": {
                "pageProps": {
                  "state": {
                    "data": {
                      "entity": {
                        "name": "Road Trip",
                        "trackList": [
                          {"title":"Instant Crush","subtitle":"Daft Punk","duration":337000},
                          {"title":"Midnight City","subtitle":"M83","duration":244000}
                        ]
                      }
                    }
                  }
                }
              }
            }
            </script></html>
        """.trimIndent()

        val playlist = repository.parseSpotifyEmbed(html)

        assertEquals(MusicImportService.SPOTIFY, playlist.service)
        assertEquals("Road Trip", playlist.name)
        assertEquals(2, playlist.tracks.size)
        assertEquals("Daft Punk", playlist.tracks.first().artist)
        assertEquals(337_000L, playlist.tracks.first().durationMs)
    }

    @Test
    fun `Apple page metadata ignores duplicate song descriptors`() {
        val html = """
            <script id="serialized-server-data" type="application/json">
            [
              {
                "contentDescriptor":{"kind":"playlist"},
                "title":"Favoris du moment",
                "children":[
                  {
                    "contentDescriptor":{
                      "kind":"song",
                      "identifiers":{"storeAdamID":"101"}
                    },
                    "title":"Tout oublier",
                    "artistName":"Angèle",
                    "duration":"3:22"
                  },
                  {
                    "contentDescriptor":{
                      "kind":"song",
                      "identifiers":{"storeAdamID":"101"}
                    },
                    "title":"Tout oublier",
                    "artistName":"Angèle",
                    "duration":"3:22"
                  }
                ]
              }
            ]
            </script>
        """.trimIndent()

        val playlist = repository.parseAppleMusicPage(html)

        assertEquals(MusicImportService.APPLE_MUSIC, playlist.service)
        assertEquals("Favoris du moment", playlist.name)
        assertEquals(1, playlist.tracks.size)
        assertEquals("Angèle", playlist.tracks.single().artist)
        assertEquals(202_000L, playlist.tracks.single().durationMs)
    }

    @Test
    fun `Deezer public API metadata becomes an import playlist`() {
        val body = """
            {
              "title":"Sport",
              "tracks":{
                "data":[
                  {
                    "title":"Stronger",
                    "duration":311,
                    "artist":{"name":"Kanye West"}
                  }
                ]
              }
            }
        """.trimIndent()

        val playlist = repository.parseDeezerPlaylist(body)

        assertEquals(MusicImportService.DEEZER, playlist.service)
        assertEquals("Sport", playlist.name)
        assertEquals("Kanye West", playlist.tracks.single().artist)
        assertEquals(311_000L, playlist.tracks.single().durationMs)
    }
}
