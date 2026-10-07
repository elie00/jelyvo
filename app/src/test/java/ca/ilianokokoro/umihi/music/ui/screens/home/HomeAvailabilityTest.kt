package ca.ilianokokoro.umihi.music.ui.screens.home

import ca.ilianokokoro.umihi.music.models.Song
import org.junit.Assert.*
import org.junit.Test

class HomeAvailabilityTest {
    @Test fun `signed out and failed network keep local listening visible`() {
        for (state in listOf(ScreenState.LoggedOut, ScreenState.Loading, ScreenState.Error(Exception()))) {
            val home = HomeState(screenState = state, recentlyPlayed = listOf(Song(youtubeId = "local")))
            assertTrue(home.visibleScreenState is ScreenState.LoggedIn)
            assertSame(state, home.screenState)
        }
    }
    @Test fun `empty local library keeps login loading and error states actionable`() {
        for (state in listOf(ScreenState.LoggedOut, ScreenState.Loading, ScreenState.Error(Exception()))) {
            assertSame(state, HomeState(screenState = state).visibleScreenState)
        }
    }
    @Test fun `remote feed remains intact when local data exists`() {
        val feed = ScreenState.LoggedIn()
        assertSame(feed, HomeState(screenState = feed, recentlyPlayed = listOf(Song(youtubeId = "local"))).visibleScreenState)
    }
}
