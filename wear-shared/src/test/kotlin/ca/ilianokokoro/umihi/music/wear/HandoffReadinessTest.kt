package ca.ilianokokoro.umihi.music.wear

import org.junit.Assert.*
import org.junit.Test

class HandoffReadinessTest {
    @Test fun `buffering and ready but suppressed audio do not stop the source`() {
        assertFalse(handoffDestinationReady("a", "a", true, false, false))
        assertFalse(handoffDestinationReady("a", "a", true, false, true))
        assertTrue(handoffDestinationReady("a", "a", true, true, true))
    }
    @Test fun `old title cannot acknowledge a new handoff`() {
        assertFalse(handoffDestinationReady("a", "b", true, true, true))
    }
    @Test fun `a paused handoff only requires prepared matching media`() {
        assertFalse(handoffDestinationReady("a", "a", false, false, false))
        assertTrue(handoffDestinationReady("a", "a", false, false, true))
    }
}
