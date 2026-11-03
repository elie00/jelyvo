/*
 * Adapted from BitChord's IosSwitch, PillTextField and settings slider
 * (https://github.com/kushagrasinghx/BitChord), GPL-3.0.
 * Copyright (C) Kushagra Singh and BitChord contributors.
 */
package ca.ilianokokoro.umihi.music.ui.components.studio

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider as MaterialSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SwitchTrackWidth = 64.dp
private val SwitchTrackHeight = 28.dp
private val SwitchThumbWidth = 40.dp
private val SwitchThumbHeight = 24.dp
private val SwitchThumbPadding = 2.dp

/**
 * A wide capsule and a white pill of a thumb that stretches toward where it is going while pressed.
 * Takes Material's call shape, so a row adopts it by import alone. Green whatever the theme: the one
 * place colour stays, because it is what makes it read as a switch.
 */
@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") thumbContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") colors: SwitchColors? = null,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val position by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 700f),
        label = "switchThumb",
    )
    val stretch by animateFloatAsState(
        targetValue = if (pressed) 1.18f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 900f),
        label = "switchStretch",
    )
    val track by animateColorAsState(
        targetValue = when {
            checked && dark -> Color(0xFF30D158)
            checked -> Color(0xFF34C759)
            else -> Color(0xFF8E8E93).copy(alpha = if (dark) 0.36f else 0.22f)
        },
        label = "switchTrack",
    )
    val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr

    val haptics = LocalHapticFeedback.current
    // Buzzes when the value flips, not on a tap: most rows also toggle from their own click.
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(checked) {
        if (settled) {
            haptics.performHapticFeedback(
                if (checked) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff
            )
        }
        settled = true
    }

    Box(
        modifier = modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .then(
                if (onCheckedChange != null) {
                    Modifier
                        .minimumInteractiveComponentSize()
                        .toggleable(
                            value = checked,
                            enabled = enabled,
                            interactionSource = interaction,
                            indication = null,
                            role = Role.Switch,
                            onValueChange = onCheckedChange,
                        )
                } else {
                    Modifier
                }
            )
            .size(SwitchTrackWidth, SwitchTrackHeight)
            .clip(CircleShape)
            .background(track),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    val travel = (SwitchTrackWidth - SwitchThumbWidth - SwitchThumbPadding * 2).toPx()
                    val x = SwitchThumbPadding.toPx() + travel * position
                    translationX = if (ltr) x else -x
                    // Grows from the edge it is leaving, so the stretch reaches for the other side.
                    transformOrigin = TransformOrigin(if (checked == ltr) 1f else 0f, 0.5f)
                    scaleX = stretch
                }
                .size(SwitchThumbWidth, SwitchThumbHeight)
                .shadow(2.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

/** A bare track: ticks and the end-stop dot are noise when the value is spelled out beside it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val colors = SliderDefaults.colors(
        thumbColor = onSurface,
        activeTrackColor = onSurface,
        inactiveTrackColor = onSurface.copy(alpha = 0.2f),
        disabledThumbColor = onSurface.copy(alpha = 0.38f),
        disabledActiveTrackColor = onSurface.copy(alpha = 0.38f),
        disabledInactiveTrackColor = onSurface.copy(alpha = 0.12f),
    )
    // Only the overload taking a state accepts a custom track in this version of Material.
    val state = remember(steps, valueRange) {
        SliderState(value, steps = steps, trackRange = valueRange)
    }
    state.value = value
    MaterialSlider(
        state = state,
        modifier = modifier,
        enabled = enabled,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        colors = colors,
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                colors = colors,
                enabled = enabled,
                drawStopIndicator = null,
                drawTick = { _, _ -> },
            )
        },
    )
}

/**
 * A flat filled box with no outline and no floating label. An outlined field is the only thing on
 * these pages wearing a border, and its label reserves empty space whether or not there is one, so
 * the label here is what the empty field says.
 */
@Composable
fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    shape: Shape = RoundedCornerShape(11.dp),
    @Suppress("UNUSED_PARAMETER") colors: TextFieldColors? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    val hint = placeholder ?: label
    val textColor = MaterialTheme.colorScheme.onSurface
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant
    val size = 15.sp
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.heightIn(min = 40.dp),
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle.merge(TextStyle(fontSize = size, color = textColor)),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        interactionSource = interactionSource,
        decorationBox = { inner ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), shape)
                    .then(
                        if (isError) {
                            Modifier.border(1.dp, MaterialTheme.colorScheme.error, shape)
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                CompositionLocalProvider(LocalContentColor provides hintColor) {
                    leadingIcon?.invoke()
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty() && hint != null) {
                        CompositionLocalProvider(
                            LocalContentColor provides hintColor,
                            LocalTextStyle provides MaterialTheme.typography.bodyMedium.copy(fontSize = size),
                        ) {
                            hint()
                        }
                    }
                    inner()
                }
                CompositionLocalProvider(LocalContentColor provides hintColor) {
                    trailingIcon?.invoke()
                }
            }
        },
    )
}
