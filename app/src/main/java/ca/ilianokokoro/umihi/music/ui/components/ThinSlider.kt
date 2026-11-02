/*
 * Adapted from BitChord's ThinSlider (https://github.com/kushagrasinghx/BitChord), GPL-3.0.
 * Copyright (C) Kushagra Singh and BitChord contributors.
 * Reduced to the plain scrubber: no Automix sheen or transition window.
 */
package ca.ilianokokoro.umihi.music.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp

private val IDLE_HEIGHT = 7.dp
private val ACTIVE_HEIGHT = 12.dp

/**
 * A capsule with no thumb, which thickens under the finger and settles back on release. Material's
 * Slider always draws a thumb and a tall track, so this is drawn directly.
 *
 * @param value position along the bar, from 0 to 1.
 * @param onValueChangeFinished called when the finger lifts.
 */
@Composable
fun ThinSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: () -> Unit = {},
    activeColor: Color = MaterialTheme.colorScheme.onSurface,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.26f),
) {
    var dragging by remember { mutableStateOf(false) }
    val barHeight by animateDpAsState(
        targetValue = if (dragging) ACTIVE_HEIGHT else IDLE_HEIGHT,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "thinSliderHeight",
    )
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnFinished by rememberUpdatedState(onValueChangeFinished)
    val fraction = value.coerceIn(0f, 1f)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            // The bar is thin, the touch target is not.
            .height(ACTIVE_HEIGHT + 22.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                if (enabled) {
                    setProgress { target ->
                        currentOnValueChange(target.coerceIn(0f, 1f))
                        currentOnFinished()
                        true
                    }
                } else {
                    disabled()
                }
            }
            // One loop for taps and drags: a separate drag detector would swallow every tap.
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    dragging = true
                    currentOnValueChange((down.position.x / size.width).coerceIn(0f, 1f))
                    while (true) {
                        val pointer = awaitPointerEvent().changes
                            .firstOrNull { it.id == down.id } ?: break
                        if (!pointer.pressed) {
                            pointer.consume()
                            break
                        }
                        currentOnValueChange((pointer.position.x / size.width).coerceIn(0f, 1f))
                        pointer.consume()
                    }
                    dragging = false
                    currentOnFinished()
                }
            },
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(barHeight)
        ) {
            val radius = CornerRadius(size.height / 2f)
            drawRoundRect(color = inactiveColor, cornerRadius = radius)
            drawRoundRect(
                color = activeColor,
                topLeft = Offset.Zero,
                size = Size(size.width * fraction, size.height),
                cornerRadius = radius,
            )
        }
    }
}
