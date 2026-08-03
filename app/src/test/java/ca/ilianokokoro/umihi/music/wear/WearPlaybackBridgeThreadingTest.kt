package ca.ilianokokoro.umihi.music.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Guards the release-only S24 crash caused by building and using MediaController on two threads. */
class WearPlaybackBridgeThreadingTest {
    private fun sourceFile(): File {
        val relative = "src/main/java/ca/ilianokokoro/umihi/music/wear/WearPlaybackBridgeService.kt"
        val direct = File(relative)
        return if (direct.exists()) direct else File("app", relative)
    }

    @Test
    fun `controller is built and consumed inside the main executor`() {
        val method = sourceFile().readText()
            .substringAfter("private fun withController(")
            .substringBefore("private fun sendResult(")

        val executorBoundary = method.indexOf("mainExecutor.execute")
        val controllerBuilder = method.indexOf("MediaController.Builder")
        val controllerAction = method.indexOf("action(controller)")

        assertTrue("missing main-executor boundary", executorBoundary >= 0)
        assertTrue("MediaController is built before entering main executor", controllerBuilder > executorBoundary)
        assertTrue("controller action escapes its application thread", controllerAction > controllerBuilder)
    }

    @Test
    fun `controller release is centralized in withController`() {
        val source = sourceFile().readText()

        assertEquals(1, Regex("controller\\.release\\(\\)").findAll(source).count())
        assertTrue(source.contains("finally {\n                            controller.release()"))
    }
}
