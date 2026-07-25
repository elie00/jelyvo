package ca.ilianokokoro.umihi.music.core.youtube

import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.AudioQuality
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class AudioQualitySelectorTest {

    private fun formats(json: String): JsonArray =
        Json.parseToJsonElement(json) as JsonArray

    @Test
    fun `quality maps to bottom middle and top of the ladder`() {
        val ladder = listOf(64, 128, 256)
        assertEquals(64, selectAudioStream(ladder, AudioQuality.Low) { it })
        assertEquals(128, selectAudioStream(ladder, AudioQuality.Normal) { it })
        assertEquals(256, selectAudioStream(ladder, AudioQuality.High) { it })
    }

    @Test
    fun `normal takes the lower middle on an even ladder`() {
        assertEquals(
            128,
            selectAudioStream(listOf(256, 64, 192, 128), AudioQuality.Normal) { it },
        )
    }

    @Test
    fun `a single format works for every quality and an empty ladder returns null`() {
        AudioQuality.entries.forEach { quality ->
            assertEquals(128, selectAudioStream(listOf(128), quality) { it })
            assertNull(selectAudioStream(emptyList<Int>(), quality) { it })
        }
    }

    @Test
    fun `high preserves first-match behaviour on equal bitrates`() {
        data class Stream(val id: String, val bitrate: Int)
        val first = Stream("first", 256)
        val second = Stream("second", 256)
        assertSame(
            first,
            selectAudioStream(listOf(first, second), AudioQuality.High) { it.bitrate },
        )
    }

    @Test
    fun `adaptive formats discard video and unusable entries`() {
        val ladder = formats(
            """
            [
              { "url": "https://cdn/video", "mimeType": "video/mp4", "bitrate": 900000 },
              { "url": "", "mimeType": "audio/webm", "bitrate": 500000 },
              { "url": "https://cdn/low", "mimeType": "audio/webm", "bitrate": 50000 },
              { "url": "https://cdn/mid", "mimeType": "audio/mp4", "bitrate": 130000 },
              { "url": "https://cdn/high", "mimeType": "audio/webm", "bitrate": 260000 }
            ]
            """
        )

        assertEquals("https://cdn/low", selectAudioStreamUrl(ladder, AudioQuality.Low))
        assertEquals("https://cdn/mid", selectAudioStreamUrl(ladder, AudioQuality.Normal))
        assertEquals("https://cdn/high", selectAudioStreamUrl(ladder, AudioQuality.High))
    }

    @Test
    fun `missing bitrates become the low pick`() {
        val ladder = formats(
            """
            [
              { "url": "https://cdn/unknown", "mimeType": "audio/webm" },
              { "url": "https://cdn/known", "mimeType": "audio/webm", "bitrate": 130000 }
            ]
            """
        )
        assertEquals("https://cdn/unknown", selectAudioStreamUrl(ladder, AudioQuality.Low))
        assertEquals("https://cdn/known", selectAudioStreamUrl(ladder, AudioQuality.High))
    }

    @Test
    fun `missing adaptive formats return null`() {
        assertNull(selectAudioStreamUrl(null, AudioQuality.High))
        assertNull(selectAudioStreamUrl(formats("[]"), AudioQuality.High))
    }
}
