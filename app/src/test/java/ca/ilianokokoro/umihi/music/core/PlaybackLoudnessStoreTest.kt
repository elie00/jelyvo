package ca.ilianokokoro.umihi.music.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies the ReplayGain-style loudness normalization: a track's stored playback volume is
 * derived from YouTube's `loudnessDb` (dB above the target reference) as `10^(-dbAboveTarget/20)`,
 * attenuation-only (never boosted above 1f, so it can never clip) and floored at 0.25f so a very
 * loud track is not driven to near silence.
 */
class PlaybackLoudnessStoreTest {

    @Test
    fun `null loudness leaves the track at full volume`() {
        PlaybackLoudnessStore.update("null-loudness", null)
        assertEquals(1f, PlaybackLoudnessStore.volumeFor("null-loudness"), 0f)
    }

    @Test
    fun `zero dB above target maps to full volume`() {
        PlaybackLoudnessStore.update("at-target", 0.0)
        assertEquals(1f, PlaybackLoudnessStore.volumeFor("at-target"), 0.0001f)
    }

    @Test
    fun `a track quieter than target is never boosted above full volume`() {
        // Negative dBAboveTarget (already quieter than reference) is clamped to 0 -> volume 1f.
        PlaybackLoudnessStore.update("quiet-track", -8.0)
        assertEquals(1f, PlaybackLoudnessStore.volumeFor("quiet-track"), 0f)
    }

    @Test
    fun `a louder track is attenuated by the expected gain`() {
        // +6 dB above target -> 10^(-6/20) ~= 0.501.
        PlaybackLoudnessStore.update("loud-track", 6.0)
        assertEquals(0.501f, PlaybackLoudnessStore.volumeFor("loud-track"), 0.005f)
    }

    @Test
    fun `an extremely loud track is floored at the minimum volume`() {
        // +20 dB would give 0.1, below the 0.25 floor.
        PlaybackLoudnessStore.update("very-loud-track", 20.0)
        assertEquals(0.25f, PlaybackLoudnessStore.volumeFor("very-loud-track"), 0f)
    }

    @Test
    fun `non-finite loudness falls back to full volume`() {
        PlaybackLoudnessStore.update("nan-loudness", Double.NaN)
        assertEquals(1f, PlaybackLoudnessStore.volumeFor("nan-loudness"), 0f)
    }

    @Test
    fun `a blank video id is ignored and reports the default volume`() {
        PlaybackLoudnessStore.update("", 6.0)
        assertEquals(1f, PlaybackLoudnessStore.volumeFor(""), 0f)
    }

    @Test
    fun `an unknown track reports full volume`() {
        assertEquals(1f, PlaybackLoudnessStore.volumeFor("never-stored"), 0f)
    }
}
