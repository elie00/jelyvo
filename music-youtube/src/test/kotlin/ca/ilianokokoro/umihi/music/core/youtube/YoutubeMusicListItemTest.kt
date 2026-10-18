package ca.ilianokokoro.umihi.music.core.youtube

import org.junit.Assert.*
import org.junit.Test

class YoutubeMusicListItemTest {
    private fun row(id: String, title: String = "Mon titre", extra: String = "") = """{
      "musicResponsiveListItemRenderer": {
        "playlistItemData":{"videoId":"$id"},
        "thumbnail":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[
          {"url":"small","width":60},{"url":"large","width":544}
        ]}}},
        "flexColumns":[
          {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"$title"}]}}},
          {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[
            {"text":"Mon artiste","navigationEndpoint":{"browseEndpoint":{"browseId":"UC42"}}},
            {"text":" • "},{"text":"3:41"}
          ]}}}
        ]$extra
      }
    }"""

    @Test fun `search rows read the subtitle duration and the artist channel`() {
        val track = parseMusicListItems("""{"contents":[${row("aB0-_123456")}]}""").single()
        assertEquals("Mon titre", track.title)
        assertEquals("Mon artiste", track.artist.name)
        assertEquals("UC42", track.artist.id)
        assertEquals("large", track.artworkUrl)
        assertEquals(221_000L, track.durationMs)
        assertFalse(track.explicit)
    }

    @Test fun `duplicate ids and malformed ids are dropped`() {
        val tracks = parseMusicListItems(
            """{"contents":[${row("aB0-_123456")},${row("aB0-_123456")},${row("short")}]}"""
        )
        assertEquals(listOf("aB0-_123456"), tracks.map { it.id })
    }

    @Test fun `rows without a title are skipped and explicit badges are kept`() {
        val badge = ""","badges":[{"musicInlineBadgeRenderer":{"icon":{"iconType":"MUSIC_EXPLICIT_BADGE"}}}]"""
        val tracks = parseMusicListItems(
            """{"contents":[${row("aB0-_123456", title = "")},${row("bB0-_123456", extra = badge)}]}"""
        )
        assertEquals(listOf("bB0-_123456"), tracks.map { it.id })
        assertTrue(tracks.single().explicit)
    }
}
