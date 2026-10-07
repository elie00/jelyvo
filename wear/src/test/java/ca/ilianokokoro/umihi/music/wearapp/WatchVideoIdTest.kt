package ca.ilianokokoro.umihi.music.wearapp

import org.junit.Assert.*
import org.junit.Test

class WatchVideoIdTest {
    @Test fun `only canonical youtube ids can address download files`() {
        assertTrue(isWatchVideoId("aB0-_123456"))
        listOf("", "../settings", "../../prefs", "/etc/passwd", "content://audio/1", "local:12345", "abc\n1234567").forEach {
            assertFalse(it, isWatchVideoId(it))
        }
    }
}
