package ca.ilianokokoro.umihi.music.core.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossfadeRulesTest {
    @Test
    fun `gains hold constant power across the fade`() {
        listOf(0f, 0.1f, 0.25f, 0.5f, 0.75f, 0.9f, 1f).forEach { progress ->
            val out = CrossfadeRules.outgoingGain(progress)
            val incoming = CrossfadeRules.incomingGain(progress)
            assertEquals(1f, out * out + incoming * incoming, 0.0001f)
        }
    }

    @Test
    fun `fade runs from outgoing only to incoming only`() {
        assertEquals(1f, CrossfadeRules.outgoingGain(0f), 0.0001f)
        assertEquals(0f, CrossfadeRules.incomingGain(0f), 0.0001f)
        assertEquals(0f, CrossfadeRules.outgoingGain(1f), 0.0001f)
        assertEquals(1f, CrossfadeRules.incomingGain(1f), 0.0001f)
    }

    @Test
    fun `progress outside the fade is clamped`() {
        assertEquals(1f, CrossfadeRules.outgoingGain(-1f), 0.0001f)
        assertEquals(1f, CrossfadeRules.incomingGain(2f), 0.0001f)
    }

    @Test
    fun `short fades and short tracks do not overlap`() {
        assertFalse(CrossfadeRules.canOverlap(fadeMs = 1_000L, durationMs = 200_000L))
        assertFalse(CrossfadeRules.canOverlap(fadeMs = 6_000L, durationMs = 11_000L))
        assertFalse(CrossfadeRules.canOverlap(fadeMs = 6_000L, durationMs = -1L))
        assertTrue(CrossfadeRules.canOverlap(fadeMs = 6_000L, durationMs = 12_000L))
    }

    @Test
    fun `tail is prepared shortly before the fade starts`() {
        val duration = 200_000L
        val fade = 6_000L

        assertEquals(194_000L, CrossfadeRules.fadeStartMs(duration, fade))
        assertFalse(CrossfadeRules.shouldArm(188_999L, duration, fade))
        assertTrue(CrossfadeRules.shouldArm(189_000L, duration, fade))
        assertTrue(CrossfadeRules.shouldArm(193_999L, duration, fade))
        assertFalse(CrossfadeRules.shouldArm(194_000L, duration, fade))
    }

    @Test
    fun `seeking past the start abandons the fade`() {
        val duration = 200_000L
        val fade = 6_000L

        assertFalse(CrossfadeRules.isTooLateToStart(194_300L, duration, fade))
        assertTrue(CrossfadeRules.isTooLateToStart(194_301L, duration, fade))
    }
}
