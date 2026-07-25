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
    fun `swiping toward the leading edge likes the song only when logged in`() {
        assertEquals(
            SongSwipeAction.AddToLiked,
            songSwipeAction(SwipeToDismissBoxValue.StartToEnd, isLoggedIn = true)
        )
        assertNull(songSwipeAction(SwipeToDismissBoxValue.StartToEnd, isLoggedIn = false))
    }

    @Test
    fun `a settled row runs nothing`() {
        assertNull(songSwipeAction(SwipeToDismissBoxValue.Settled, isLoggedIn = true))
        assertNull(songSwipeAction(SwipeToDismissBoxValue.Settled, isLoggedIn = false))
    }
}
