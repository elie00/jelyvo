package ca.ilianokokoro.umihi.music

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppProcessTest {
    @Test
    fun `only the package process performs full app startup`() {
        assertTrue(isMainAppProcess("ca.ilianokokoro.umihi.music", "ca.ilianokokoro.umihi.music"))
        assertFalse(
            isMainAppProcess(
                "ca.ilianokokoro.umihi.music:wear_bridge",
                "ca.ilianokokoro.umihi.music",
            )
        )
    }

    @Test
    fun `unknown process keeps backward compatible startup`() {
        assertTrue(isMainAppProcess(null, "ca.ilianokokoro.umihi.music"))
    }
}
