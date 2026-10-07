package ca.ilianokokoro.umihi.music.core.youtube

import org.junit.Assert.*
import org.junit.Test

class YoutubeTrackPageTest {
    @Test fun `account rows preserve metadata and continuation without counting menu entries`() {
        val page = parseYoutubeTrackPage("""{
          "contents":[{"musicResponsiveListItemRenderer":{
            "playlistItemData":{"videoId":"aB0-_123456"},
            "flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Song"}]}}},
              {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Artist","navigationEndpoint":{"browseEndpoint":{"browseId":"UCtest"}}}]}}}],
            "fixedColumns":[{"text":{"runs":[{"text":"3:20"}]}}],
            "thumbnail":{"thumbnails":[{"url":"small","width":60},{"url":"large","width":500}]}
          }}],"continuations":[{"nextContinuationData":{"continuation":"next"}}],
          "menu":{"watchEndpoint":{"videoId":"bB0-_123456"}}
        }""")
        assertEquals(1, page.tracks.size)
        val track = page.tracks.single()
        assertEquals("Song", track.title)
        assertEquals("Artist", track.artist.name)
        assertEquals(200000L, track.durationMs)
        assertEquals("large", track.artworkUrl)
        assertEquals("next", page.continuation)
        assertFalse(page.signedOut)
    }
    @Test fun `empty library is distinct from a sign in response`() {
        assertFalse(parseYoutubeTrackPage("{}").signedOut)
        assertTrue(parseYoutubeTrackPage("""{"responseContext":{"mainAppWebResponseContext":{"loggedOut":true}}}""").signedOut)
        assertTrue(parseYoutubeTrackPage("""{"button":{"signInEndpoint":{}}}""").signedOut)
    }
}
