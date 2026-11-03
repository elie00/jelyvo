/*
 * Adapted from BitChord's AlertScaffold and AlertAction
 * (https://github.com/kushagrasinghx/BitChord), GPL-3.0.
 * Copyright (C) Kushagra Singh and BitChord contributors.
 */
package ca.ilianokokoro.umihi.music.ui.components.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

val ALERT_WIDTH = 270.dp

/** For alerts holding a form or a list, which 270dp would cramp. */
val ALERT_WIDTH_WIDE = 320.dp

private val ALERT_CORNER = 14.dp
private val ACTION_HEIGHT = 44.dp
private const val SCRIM_ALPHA = 0.28f
private const val MAX_HEIGHT_FRACTION = 0.85f

private enum class AlertActionRole { Confirm, Dismiss }

private val LocalAlertActionRole = compositionLocalOf<AlertActionRole?> { null }

/**
 * A centred card with a centred title and stacked full-width actions. Takes Material's call shape,
 * so a screen adopts it by import alone, along with [TextButton], which turns into an action row
 * when it sits in one of the action slots.
 */
@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
    width: Dp = ALERT_WIDTH,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = properties.dismissOnBackPress,
            dismissOnClickOutside = properties.dismissOnClickOutside,
            usePlatformDefaultWidth = false,
        ),
    ) {
        val view = LocalView.current
        remember(view) { (view.parent as? DialogWindowProvider)?.window?.setDimAmount(SCRIM_ALPHA) }
        AlertCard(
            confirmButton = confirmButton,
            modifier = modifier,
            dismissButton = dismissButton,
            title = title,
            text = text,
            width = width,
        )
    }
}

/** The card alone: a window of its own cannot be captured in a test. */
@Composable
internal fun AlertCard(
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    width: Dp = ALERT_WIDTH,
) {
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * MAX_HEIGHT_FRACTION
    Column(
        modifier = modifier
            .width(width)
            .heightIn(max = maxHeight)
            .clip(RoundedCornerShape(ALERT_CORNER))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            // Gives way to the actions on a long list, which then scrolls inside its own slot.
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 19.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                title?.let {
                    ProvideTextStyle(
                        MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.W600,
                            textAlign = TextAlign.Center,
                        ),
                    ) { it() }
                }
                text?.let {
                    ProvideTextStyle(
                        MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Center,
                        ),
                    ) { it() }
                }
            }
            AlertRule()
            CompositionLocalProvider(LocalAlertActionRole provides AlertActionRole.Confirm) {
                confirmButton()
            }
            dismissButton?.let {
                AlertRule()
                CompositionLocalProvider(LocalAlertActionRole provides AlertActionRole.Dismiss) {
                    it()
                }
            }
        }
    }
}

@Composable
fun AlertRule(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)),
    )
}

/**
 * Plain text that washes on press rather than rippling. In an alert's action slot it is a full-width
 * row, the confirming one in a heavier weight. Takes Material's call shape.
 */
@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors? = null,
    @Suppress("UNUSED_PARAMETER") shapes: ButtonShapes? = null,
    contentPadding: PaddingValues? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val role = LocalAlertActionRole.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val onSurface = MaterialTheme.colorScheme.onSurface
    val color = colors?.contentColor ?: if (role == null) onSurface else MaterialTheme.colorScheme.primary
    val wash = if (pressed) onSurface.copy(alpha = 0.09f) else androidx.compose.ui.graphics.Color.Transparent
    val style: TextStyle = if (role == null) {
        MaterialTheme.typography.labelLarge
    } else {
        MaterialTheme.typography.bodyLarge.copy(
            fontSize = 17.sp,
            fontWeight = if (role == AlertActionRole.Confirm) FontWeight.W600 else FontWeight.W400,
        )
    }
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .then(
                if (role == null) {
                    Modifier.clip(RoundedCornerShape(8.dp))
                } else {
                    Modifier
                        .fillMaxWidth()
                        .height(ACTION_HEIGHT)
                }
            )
            .background(wash)
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = interaction,
                onClick = onClick,
            )
            .alpha(if (enabled) 1f else 0.4f)
            .then(
                if (role == null) {
                    Modifier.padding(contentPadding ?: PaddingValues(horizontal = 12.dp, vertical = 8.dp))
                } else {
                    Modifier
                }
            ),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides color,
            LocalTextStyle provides style,
        ) {
            content()
        }
    }
}
