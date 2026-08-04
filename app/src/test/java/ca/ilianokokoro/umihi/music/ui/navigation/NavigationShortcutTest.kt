package ca.ilianokokoro.umihi.music.ui.navigation

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationShortcutTest {
    @Test
    fun `command number shortcuts select primary destinations`() {
        assertEquals(HomeScreenKey, navigationShortcutDestination(Key.One, true))
        assertEquals(SearchScreenKey(), navigationShortcutDestination(Key.Two, true))
        assertEquals(LibraryScreenKey, navigationShortcutDestination(Key.Three, true))
    }

    @Test
    fun `command four or comma opens settings`() {
        assertEquals(SettingsScreenKey, navigationShortcutDestination(Key.Four, true))
        assertEquals(SettingsScreenKey, navigationShortcutDestination(Key.Comma, true))
    }

    @Test
    fun `plain typing and unrelated keys are ignored`() {
        assertNull(navigationShortcutDestination(Key.One, false))
        assertNull(navigationShortcutDestination(Key.DirectionLeft, true))
    }
}
