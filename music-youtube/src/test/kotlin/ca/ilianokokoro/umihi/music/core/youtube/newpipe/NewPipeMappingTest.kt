package ca.ilianokokoro.umihi.music.core.youtube.newpipe

import ca.ilianokokoro.umihi.music.core.domain.Track
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NewPipeMappingTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun `canonical share and watch links preserve the exact id`() {
        for (url in listOf("https://www.youtube.com/watch?v=aB0-_123456&t=4", "https://music.youtube.com/watch?list=test&v=aB0-_123456", "https://youtu.be/aB0-_123456?si=abc", "https://www.youtube.com/shorts/aB0-_123456")) {
            assertEquals(url, "aB0-_123456", videoIdOf(url))
        }
    }
    @Test fun `unrelated query parameters domains and paths are not video ids`() {
        for (url in listOf("https://evil.test/watch?v=aB0-_123456", "https://youtube.com/watch?sv=aB0-_123456", "https://youtube.com/watch?v=../settings", "https://youtube.com/watch?v=", "not a url")) {
            assertNull(url, videoIdOf(url))
        }
    }
    @Test fun `playlist links are normalized without arbitrary hosts or parameters`() {
        assertEquals("https://www.youtube.com/playlist?list=PLtest", youtubePlaylistUrl("https://music.youtube.com/watch?v=aB0-_123456&list=PLtest&si=secret"))
        assertNull(youtubePlaylistUrl("https://evil.test/playlist?list=PLtest"))
        assertNull(youtubePlaylistUrl("https://www.youtube.com/playlist?list=../etc"))
    }
    @Test fun `downloaded file becomes a playable file URI without network resolution`() = runBlocking {
        val file = temp.newFile("audio with space.mp4").apply { writeText("audio") }
        val resolved = NewPipeStreamResolver().resolve(Track("a", offlineAudioPath = file.path), allowLocal = true)
        assertEquals(file.toURI().toString(), resolved.url)
    }
}
