package ca.ilianokokoro.umihi.music.core.managers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateResumePlanTest {
    @Test
    fun `partial content resumes after the existing bytes`() {
        val plan = updateResumePlan(existingBytes = 2_048L, responseCode = 206)

        assertEquals(2_048L, plan.retainedBytes)
        assertTrue(plan.append)
    }

    @Test
    fun `full response replaces a partial file when server ignores range`() {
        val plan = updateResumePlan(existingBytes = 2_048L, responseCode = 200)

        assertEquals(0L, plan.retainedBytes)
        assertFalse(plan.append)
    }
}
