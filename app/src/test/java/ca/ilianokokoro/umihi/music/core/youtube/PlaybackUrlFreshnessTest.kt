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
}
