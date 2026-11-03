/*
 * Appearance adapted from BitChord's spinners, progress bars and selection marks
 * (https://github.com/kushagrasinghx/BitChord), GPL-3.0.
 * Copyright (C) Kushagra Singh and BitChord contributors.
 */
package ca.ilianokokoro.umihi.music.ui.components.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator as MaterialCircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconToggleButtonColors
import androidx.compose.material3.LinearProgressIndicator as MaterialLinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected as selectedSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val ICON_BUTTON_VISUAL = 40.dp
private const val DISABLED_ICON_ALPHA = 0.4f
private const val TRACK_ALPHA = 0.2f

/** A thin ring and no track. Takes Material's call shape, so a screen adopts it by import alone. */
@Composable
fun CircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    strokeWidth: Dp = 2.dp,
) {
    MaterialCircularProgressIndicator(
        modifier = modifier,
        color = color,
        strokeWidth = strokeWidth,
        trackColor = Color.Transparent,
        strokeCap = StrokeCap.Round,
    )
}

/** What a whole page shows while it loads: the same ring, not a shape that morphs. */
@Composable
fun LoadingIndicator(modifier: Modifier = Modifier) {
    CircularProgressIndicator(modifier = modifier.size(28.dp), strokeWidth = 2.5.dp)
}

/** A bare bar: no gap and no end-stop dot, which are noise when the value is spelled out beside it. */
@Composable
fun LinearProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    trackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = TRACK_ALPHA),
) {
    MaterialLinearProgressIndicator(
        progress = progress,
        modifier = modifier,
        color = color,
        trackColor = trackColor,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

@Composable
fun LinearProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    trackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = TRACK_ALPHA),
) {
    MaterialLinearProgressIndicator(
        modifier = modifier,
        color = color,
        trackColor = trackColor,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
    )
}

/**
 * A tick on the chosen row and nothing on the others, in a fixed slot so their labels line up. With no
 * [onClick] the row around it carries the role and the state, as Material's radio button expects too.
 */
@Composable
fun RadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
                        .semantics { selectedSemantics = selected }
                } else {
                    Modifier
                }
            )
            .size(24.dp)
            .alpha(if (enabled) 1f else DISABLED_ICON_ALPHA),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** A hairline, in the text colour so it follows any page. Takes Material's call shape. */
@Composable
fun HorizontalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = 0.5.dp,
    color: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(thickness)
            .background(color),
    )
}

/**
 * A bare glyph with a round target that keeps 48dp to aim at, without the corner that morphs on press.
 * Takes Material's call shape.
 */
@Composable
fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val tint = colors?.contentColor ?: LocalContentColor.current
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(ICON_BUTTON_VISUAL)
            .alpha(if (enabled) 1f else DISABLED_ICON_ALPHA)
            .clip(CircleShape)
            .background(colors?.containerColor ?: Color.Transparent)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides tint) {
            content()
        }
    }
}

/** [IconButton] with a state. What it looks like while on comes from [colors], where a screen asks. */
@Composable
fun IconToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconToggleButtonColors? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val tint = when {
        colors == null -> LocalContentColor.current
        checked -> colors.checkedContentColor
        else -> colors.contentColor
    }
    val fill = when {
        colors == null -> Color.Transparent
        checked -> colors.checkedContainerColor
        else -> colors.containerColor
    }
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(ICON_BUTTON_VISUAL)
            .alpha(if (enabled) 1f else DISABLED_ICON_ALPHA)
            .clip(CircleShape)
            .background(fill)
            .clickable(enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) }
            .semantics { selectedSemantics = checked },
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides tint) {
            content()
        }
    }
}
