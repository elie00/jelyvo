package ca.ilianokokoro.umihi.music.ui.components

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardContextMenuModifierTest {
    @Test
    fun `menu key opens context menu without modifiers`() {
        assertTrue(
            isKeyboardContextMenuShortcut(
                key = Key.Menu,
                isShiftPressed = false,
            )
        )
    }

    @Test
    fun `shift f10 opens context menu`() {
        assertTrue(
            isKeyboardContextMenuShortcut(
                key = Key.F10,
                isShiftPressed = true,
            )
        )
    }

    @Test
    fun `plain f10 does not open context menu`() {
        assertFalse(
            isKeyboardContextMenuShortcut(
                key = Key.F10,
                isShiftPressed = false,
            )
        )
    }

    @Test
    fun `unrelated key does not open context menu`() {
        assertFalse(
            isKeyboardContextMenuShortcut(
                key = Key.Enter,
                isShiftPressed = true,
            )
        )
    }
}
