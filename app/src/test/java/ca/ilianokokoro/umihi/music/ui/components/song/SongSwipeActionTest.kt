package ca.ilianokokoro.umihi.music.ui.components.song

import androidx.compose.material3.SwipeToDismissBoxValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SongSwipeActionTest {

    @Test
    fun `swiping toward the trailing edge queues the song`() {
        assertEquals(
            SongSwipeAction.AddToQueue,
            songSwipeAction(SwipeToDismissBoxValue.EndToStart, isLoggedIn = true)
        )
        assertEquals(
            SongSwipeAction.AddToQueue,
            songSwipeAction(SwipeToDismissBoxValue.EndToStart, isLoggedIn = false)
        )
    }

    @Test
    fun `swiping toward the leading edge toggles liked state only when logged in`() {
        assertEquals(
            SongSwipeAction.ToggleLiked,
            songSwipeAction(SwipeToDismissBoxValue.StartToEnd, isLoggedIn = true)
        )
        assertNull(songSwipeAction(SwipeToDismissBoxValue.StartToEnd, isLoggedIn = false))
    }

    @Test
    fun `a settled row runs nothing`() {
        assertNull(songSwipeAction(SwipeToDismissBoxValue.Settled, isLoggedIn = true))
        assertNull(songSwipeAction(SwipeToDismissBoxValue.Settled, isLoggedIn = false))
    }

    @Test
    fun `session status overrides playlist context and embedded metadata`() {
        assertEquals(
            false,
            resolveSongLikeStatus(
                sessionStatus = false,
                contextualStatus = true,
                embeddedStatus = true,
            )
        )
    }

    @Test
    fun `liked playlist context fills missing metadata`() {
        assertEquals(
            true,
            resolveSongLikeStatus(
                sessionStatus = null,
                contextualStatus = true,
                embeddedStatus = null,
            )
        )
    }
}
