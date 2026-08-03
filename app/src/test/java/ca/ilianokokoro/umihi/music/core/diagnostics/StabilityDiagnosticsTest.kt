package ca.ilianokokoro.umihi.music.core.diagnostics

import android.app.ApplicationExitInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StabilityDiagnosticsTest {
    @Test
    fun `crashes anrs and Samsung freezer exits are retained`() {
        assertTrue(isUnexpectedExitReason(ApplicationExitInfo.REASON_CRASH))
        assertTrue(isUnexpectedExitReason(ApplicationExitInfo.REASON_ANR))
        assertTrue(isUnexpectedExitReason(ApplicationExitInfo.REASON_FREEZER))
    }

    @Test
    fun `normal user exits do not create a diagnostic`() {
        assertFalse(isUnexpectedExitReason(ApplicationExitInfo.REASON_USER_REQUESTED))
        assertFalse(isUnexpectedExitReason(ApplicationExitInfo.REASON_EXIT_SELF))
    }

    @Test
    fun `system descriptions are flattened and bounded`() {
        val sanitized = sanitizeDiagnosticDescription(" binder\n  transaction\t" + "x".repeat(600))

        assertFalse(sanitized.contains('\n'))
        assertEquals(500, sanitized.length)
        assertTrue(sanitized.startsWith("binder transaction"))
    }
}
