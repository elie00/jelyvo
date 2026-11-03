/*
 * Adapted from BitChord's IosMenu
 * (https://github.com/kushagrasinghx/BitChord), GPL-3.0.
 * Copyright (C) Kushagra Singh and BitChord contributors.
 */
package ca.ilianokokoro.umihi.music.ui.components.studio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A charcoal or frost slab with its own greys rather than the theme's surfaces: a menu floats over
 * artwork as often as over a page, and a tinted page would otherwise tint its menus too.
 */
@Immutable
data class StudioMenuColors(
    val fill: Color,
    val rim: Color,
    val label: Color,
    val secondaryLabel: Color,
    val separator: Color,
    val pressed: Color,
)

private val DarkMenu = StudioMenuColors(
    fill = Color(0xFF1C1C1E),
    rim = Color.White.copy(alpha = 0.10f),
    label = Color.White,
    secondaryLabel = Color.White.copy(alpha = 0.70f),
    separator = Color.White.copy(alpha = 0.10f),
    pressed = Color.White.copy(alpha = 0.10f),
)

private val LightMenu = StudioMenuColors(
    fill = Color(0xFFF6F6F7),
    rim = Color.Black.copy(alpha = 0.06f),
    label = Color(0xFF1A1A1A),
    secondaryLabel = Color(0xFF1A1A1A).copy(alpha = 0.55f),
    separator = Color.Black.copy(alpha = 0.09f),
    pressed = Color.Black.copy(alpha = 0.07f),
)

@Composable
fun studioMenuColors(): StudioMenuColors =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) DarkMenu else LightMenu

val StudioMenuShape: Shape = RoundedCornerShape(30.dp)

/** Wide enough for the longest action on one line or two. */
val StudioMenuWidth = 256.dp

/** Short enough to fit the menu's own window, which would cut a longer shadow off in a straight line. */
val StudioMenuShadow = 8.dp

private const val DISABLED_ROW_ALPHA = 0.4f

/**
 * The menu hung off a ⋯ button, in BitChord's slab and rows. Material still places it against its
 * anchor, opens it, and handles focus and dismissal. So unlike BitChord it has no scrim behind it,
 * and no frost: both need the panel drawn into the page rather than in a window of its own.
 */
@Composable
fun StudioMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Composed only while open: https://issuetracker.google.com/issues/529423493
    if (expanded) {
        val colors = studioMenuColors()
        DropdownMenu(
            expanded = true,
            onDismissRequest = onDismissRequest,
            modifier = modifier.width(StudioMenuWidth),
            shape = StudioMenuShape,
            containerColor = colors.fill,
            tonalElevation = 0.dp,
            shadowElevation = StudioMenuShadow,
            border = BorderStroke(0.5.dp, colors.rim),
            content = content,
        )
    }
}

/**
 * One line of a menu: the glyph in a fixed column, so labels line up whether a row has one or not,
 * the label, an optional dimmer line under it and an optional value at the far end. Pressing paints
 * a rounded fill under the row rather than a ripple.
 */
@Composable
fun StudioMenuItem(
    text: String,
    onClick: () -> Unit,
    leadingIcon: ImageVector?,
    modifier: Modifier = Modifier,
    description: String? = null,
    value: String? = null,
    iconTint: Color? = null,
    labelColor: Color? = null,
    enabled: Boolean = true,
) {
    val colors = studioMenuColors()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val alpha = if (enabled) 1f else DISABLED_ROW_ALPHA
    val foreground = labelColor ?: colors.label
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (pressed || focused) colors.pressed else Color.Transparent)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .heightIn(min = 44.dp)
            .padding(start = 12.dp, end = 14.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = (iconTint ?: foreground).copy(alpha = alpha),
                    modifier = Modifier.size(21.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = text,
                color = foreground.copy(alpha = alpha),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 17.sp,
                    lineHeight = 21.sp,
                    letterSpacing = (-0.3).sp,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (description != null) {
                Text(
                    text = description,
                    color = colors.secondaryLabel.copy(alpha = colors.secondaryLabel.alpha * alpha),
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (value != null) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = value,
                color = colors.secondaryLabel.copy(alpha = colors.secondaryLabel.alpha * alpha),
                fontSize = 15.sp,
                maxLines = 1,
            )
        }
    }
}

/** The break between groups: a hairline inset from the panel's edges, with air either side. */
@Composable
fun StudioMenuSeparator() {
    val colors = studioMenuColors()
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .heightIn(min = 1.dp)
            .background(colors.separator),
    )
}
