package ca.ilianokokoro.umihi.music.core.youtube

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackUrlFreshnessTest {
    @Test
    fun `fresh signed URL is accepted without a network probe`() {
        assertTrue(
            isPlaybackUrlFresh(
                url = "https://cdn.example/audio?expire=10000",
                nowEpochSeconds = 1_000,
            )
        )
    }

    @Test
    fun `URL inside safety window is rejected`() {
        assertFalse(
            isPlaybackUrlFresh(
                url = "https://cdn.example/audio?expire=1299",
                nowEpochSeconds = 1_000,
            )
        )
    }

    @Test
    fun `URL without signed expiry is rejected`() {
        assertFalse(
            isPlaybackUrlFresh(
                url = "https://cdn.example/audio",
                nowEpochSeconds = 1_000,
            )
        )
    }

    @Test
    fun `twenty minutes left is fresh locally but too short for cast`() {
        val url = "https://cdn.example/audio?expire=2200"

        assertTrue(isPlaybackUrlFresh(url = url, nowEpochSeconds = 1_000))
        assertFalse(
            isPlaybackUrlFresh(
                url = url,
                nowEpochSeconds = 1_000,
                minRemainingSeconds = CAST_URL_MIN_LIFETIME_SECONDS,
            )
        )
    }

    @Test
    fun `cast accepts a URL beyond its stricter margin`() {
        assertTrue(
            isPlaybackUrlFresh(
                url = "https://cdn.example/audio?expire=2801",
                nowEpochSeconds = 1_000,
                minRemainingSeconds = CAST_URL_MIN_LIFETIME_SECONDS,
            )
        )
    }

    @Test
    fun `freshness margin remains strict at the boundary`() {
        assertFalse(
            isPlaybackUrlFresh(
                url = "https://cdn.example/audio?expire=2800",
                nowEpochSeconds = 1_000,
                minRemainingSeconds = CAST_URL_MIN_LIFETIME_SECONDS,
            )
        )
    }
}
