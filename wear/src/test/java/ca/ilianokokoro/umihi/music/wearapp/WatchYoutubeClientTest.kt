package ca.ilianokokoro.umihi.music.wearapp

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchYoutubeClientTest {
    @Test
    fun parsesSongRowsAndDeduplicatesVideoIds() {
        val row = """{
          "musicResponsiveListItemRenderer": {
            "playlistItemData":{"videoId":"abc123"},
            "thumbnail":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[
              {"url":"small","width":60},{"url":"large","width":544}
            ]}}},
            "flexColumns":[
              {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Mon titre"}]}}},
              {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[
                {"text":"Mon artiste","navigationEndpoint":{"browseEndpoint":{"browseId":"UC42"}}},
                {"text":" • "},{"text":"3:41"}
              ]}}}
            ]
          }
        }""".trimIndent()
        val response = """{"contents":[$row,$row]}"""

        val songs = WatchYoutubeClient().parseSearchResponse(response)

        assertEquals(1, songs.size)
        assertEquals("abc123", songs.single().videoId)
        assertEquals("Mon titre", songs.single().title)
        assertEquals("Mon artiste", songs.single().artist)
        assertEquals("large", songs.single().artworkUrl)
        assertEquals("3:41", songs.single().durationText)
    }
}
