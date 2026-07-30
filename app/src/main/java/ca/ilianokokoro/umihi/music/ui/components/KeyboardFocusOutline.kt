package ca.ilianokokoro.umihi.music.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Keeps keyboard and D-pad focus visible without changing touch or mouse interaction.
 *
 * Place this modifier before the control's clickable/focusable modifier so it observes the focus
 * target created by that control.
 */
@Composable
fun Modifier.keyboardFocusOutline(
    shape: Shape = RoundedCornerShape(16.dp),
): Modifier {
    var isFocused by remember { mutableStateOf(false) }
    val outlineColor by animateColorAsState(
        targetValue = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(durationMillis = 120),
        label = "keyboardFocusOutline",
    )

    return onFocusChanged { isFocused = it.isFocused }
        .border(width = 2.dp, color = outlineColor, shape = shape)
}
