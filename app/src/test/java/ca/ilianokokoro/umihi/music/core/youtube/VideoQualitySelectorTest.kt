package ca.ilianokokoro.umihi.music.core.youtube

import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.VideoQuality
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoQualitySelectorTest {

    private fun formats(vararg entries: Pair<Int, Int>): JsonArray = Json.parseToJsonElement(
        entries.joinToString(prefix = "[", postfix = "]") { (height, bitrate) ->
            """{"url":"https://v/$height","mimeType":"video/mp4","height":$height,"bitrate":$bitrate}"""
        }
    ) as JsonArray

    @Test
    fun `only resolutions above the muxed ladder need a separate audio track`() {
        assertFalse(VideoQualitySelector.needsSeparateAudio(VideoQuality.P480))
        assertFalse(VideoQualitySelector.needsSeparateAudio(VideoQuality.P720))
        assertTrue(VideoQualitySelector.needsSeparateAudio(VideoQuality.P1080))
        assertTrue(VideoQualitySelector.needsSeparateAudio(VideoQuality.P2160))
    }

    @Test
    fun `picks the tallest track that fits under the ceiling`() {
        val ladder = formats(360 to 500, 720 to 1_500, 1080 to 3_000, 2160 to 12_000)

        assertEquals(
            "https://v/1080",
            VideoQualitySelector.selectVideoOnly(ladder, VideoQuality.P1080)
                ?.get("url")?.toString()?.trim('"'),
        )
        assertEquals(
            "https://v/2160",
            VideoQualitySelector.selectVideoOnly(ladder, VideoQuality.P2160)
                ?.get("url")?.toString()?.trim('"'),
        )
    }

    @Test
    fun `height wins over bitrate so a fatter smaller track never outranks a taller one`() {
        val ladder = formats(720 to 9_000, 1080 to 3_000)

        assertEquals(
            "https://v/1080",
            VideoQualitySelector.selectVideoOnly(ladder, VideoQuality.P1080)
                ?.get("url")?.toString()?.trim('"'),
        )
    }

    @Test
    fun `a ladder that overshoots still plays at its smallest rung`() {
        val ladder = formats(1440 to 6_000, 2160 to 12_000)

        assertEquals(
            "https://v/1440",
            VideoQualitySelector.selectVideoOnly(ladder, VideoQuality.P480)
                ?.get("url")?.toString()?.trim('"'),
        )
    }

    @Test
    fun `the muxed ladder is capped at 720p even when asked for more`() {
        val ladder = formats(360 to 500, 720 to 1_500, 1080 to 3_000)

        assertEquals(
            "https://v/720",
            VideoQualitySelector.selectMuxed(ladder, VideoQuality.P2160)
                ?.get("url")?.toString()?.trim('"'),
        )
    }

    @Test
    fun `an empty ladder selects nothing rather than crashing`() {
        assertNull(VideoQualitySelector.selectVideoOnly(null, VideoQuality.P1080))
        assertNull(VideoQualitySelector.selectMuxed(formats(), VideoQuality.P720))
    }
}
