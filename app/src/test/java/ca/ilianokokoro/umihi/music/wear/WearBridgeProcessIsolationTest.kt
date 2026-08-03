package ca.ilianokokoro.umihi.music.wear

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WearBridgeProcessIsolationTest {
    private fun moduleFile(relative: String): File {
        val direct = File(relative)
        return if (direct.exists()) direct else File("app", relative)
    }

    @Test
    fun `wear listener is isolated from the phone ui process`() {
        val manifest = moduleFile("src/main/AndroidManifest.xml").readText()
        val service = manifest.substringAfter(".wear.WearPlaybackBridgeService")
            .substringBefore("</service>")

        assertTrue(service.contains("android:process=\":wear_bridge\""))
    }

    @Test
    fun `remote bridge delegates account mutations to the playback session`() {
        val bridge = moduleFile(
            "src/main/java/ca/ilianokokoro/umihi/music/wear/WearPlaybackBridgeService.kt"
        ).readText()

        assertTrue(bridge.contains("SESSION_COMMAND_TOGGLE_LIKE"))
        assertTrue(bridge.contains("HANDOFF_TO_PHONE_PATH"))
        assertTrue(bridge.contains("decodeWearPlaybackHandoff"))
        assertFalse(bridge.contains("LikeManager"))
        assertFalse(bridge.contains("WearPlaybackStatePublisher"))
    }
}
