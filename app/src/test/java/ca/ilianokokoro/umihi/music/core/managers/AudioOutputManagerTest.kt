package ca.ilianokokoro.umihi.music.core.managers

import ca.ilianokokoro.umihi.music.core.managers.AudioOutputManager.RouteCandidate
import ca.ilianokokoro.umihi.music.core.managers.AudioOutputManager.RouteKind
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioOutputManagerTest {

    @Test
    fun `bluetooth wins over the phone speaker`() {
        val selected = AudioOutputManager.selectActiveRoute(
            listOf(
                RouteCandidate(1, "Phone", RouteKind.PHONE),
                RouteCandidate(2, "Car audio", RouteKind.BLUETOOTH),
            )
        )

        assertEquals(2, selected?.id)
    }

    @Test
    fun `usb wins over wired output`() {
        val selected = AudioOutputManager.selectActiveRoute(
            listOf(
                RouteCandidate(1, "Headphones", RouteKind.WIRED),
                RouteCandidate(2, "USB DAC", RouteKind.USB),
            )
        )

        assertEquals(RouteKind.USB, selected?.kind)
    }
}
