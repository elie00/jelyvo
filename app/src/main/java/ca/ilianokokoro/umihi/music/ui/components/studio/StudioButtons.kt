/*
 * Adapted from BitChord's ActionPill, PlayCircle, CircleIconButton and PillSegmentedControl
 * (https://github.com/kushagrasinghx/BitChord), GPL-3.0.
 * Copyright (C) Kushagra Singh and BitChord contributors.
 */
package ca.ilianokokoro.umihi.music.ui.components.studio

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.ButtonColors
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

val RELEASE_SIDE_BUTTON = 50.dp
val RELEASE_ACTION_GAP = 14.dp
val ARTIST_PLAY_BUTTON = 70.dp
val ARTIST_SIDE_BUTTON = 52.dp
val ARTIST_ACTION_GAP = 24.dp

private const val VEIL_ALPHA = 0.12f
private const val EDGE_ALPHA = 0.10f
private const val DISABLED_ALPHA = 0.4f

/** The veil and hairline of the buttons beside Play. Both follow the text colour, so light works too. */
@Composable
private fun Modifier.veil(): Modifier {
    val onSurface = MaterialTheme.colorScheme.onSurface
    return background(onSurface.copy(alpha = VEIL_ALPHA), CircleShape)
        .border(1.dp, onSurface.copy(alpha = EDGE_ALPHA), CircleShape)
}

/**
 * A labelled pill. [filled] is Play, in the strongest contrast the page has, so the primary action
 * survives every theme. Otherwise it wears the veil of the circles beside it.
 */
@Composable
fun ActionPill(
    icon: ImageVector?,
    label: String,
    onClick: () -> Unit,
    filled: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = RELEASE_SIDE_BUTTON,
    horizontalPadding: Dp = 24.dp,
) {
    val colors = MaterialTheme.colorScheme
    val content = if (filled) colors.background else colors.onSurface
    Row(
        modifier = modifier
            .height(size)
            .clip(CircleShape)
            .then(if (filled) Modifier.background(colors.onBackground) else Modifier.veil())
            .clickable(onClick = onClick)
            .padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A filled circle with the triangle punched out of it, so the page shows through. */
@Composable
fun PlayCircle(
    onClick: () -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    size: Dp = ARTIST_PLAY_BUTTON,
    iconScale: Float = 0.55f,
) {
    val container = MaterialTheme.colorScheme.onBackground
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            // Offscreen, so the clear below cuts this circle and not what is behind it.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawCircle(container)
                val box = this.size.minDimension * iconScale
                val unit = box / 24f
                val origin = Offset((this.size.width - box) / 2f, (this.size.height - box) / 2f)
                val triangle = Path().apply {
                    moveTo(origin.x + 6.8f * unit, origin.y + 4.8f * unit)
                    lineTo(origin.x + 19.2f * unit, origin.y + 12f * unit)
                    lineTo(origin.x + 6.8f * unit, origin.y + 19.2f * unit)
                    close()
                }
                drawPath(triangle, Color.Black, blendMode = BlendMode.Clear)
                drawPath(
                    triangle,
                    Color.Black,
                    style = Stroke(width = 2f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    blendMode = BlendMode.Clear,
                )
            }
            .semantics {
                contentDescription = description
                role = Role.Button
            },
    )
}

/** A round veiled button, for anything that is not simply one icon. */
@Composable
fun CircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = RELEASE_SIDE_BUTTON,
    enabled: Boolean = true,
    filled: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(CircleShape)
            .then(
                if (filled) {
                    Modifier.background(MaterialTheme.colorScheme.onBackground)
                } else {
                    Modifier.veil()
                }
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (filled) {
                MaterialTheme.colorScheme.background
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        ) {
            content()
        }
    }
}

private val BUTTON_MIN_HEIGHT = 48.dp
private val ICON_BUTTON_SIZE = 40.dp

@Composable
private fun PillButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    filled: Boolean,
    colors: ButtonColors?,
    contentPadding: PaddingValues?,
    content: @Composable RowScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val fill = when {
        colors != null -> Modifier.background(colors.containerColor)
        filled -> Modifier.background(scheme.onBackground)
        else -> Modifier.veil()
    }
    Row(
        modifier = modifier
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .heightIn(min = BUTTON_MIN_HEIGHT)
            .clip(CircleShape)
            .then(fill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(contentPadding ?: PaddingValues(horizontal = 20.dp, vertical = 8.dp)),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides (
                colors?.contentColor ?: if (filled) scheme.background else scheme.onSurface
            ),
            LocalTextStyle provides MaterialTheme.typography.titleSmall,
        ) {
            content()
        }
    }
}

/** The page's main action, in its strongest contrast. Takes Material's call shape, so a screen adopts it by import alone. */
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") shapes: ButtonShapes? = null,
    colors: ButtonColors? = null,
    contentPadding: PaddingValues? = null,
    content: @Composable RowScope.() -> Unit,
) = PillButton(onClick, modifier, enabled, filled = true, colors, contentPadding, content)

/** A secondary action in the veil of the buttons beside Play. Takes Material's call shape. */
@Composable
fun FilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") shapes: ButtonShapes? = null,
    colors: ButtonColors? = null,
    contentPadding: PaddingValues? = null,
    content: @Composable RowScope.() -> Unit,
) = PillButton(onClick, modifier, enabled, filled = false, colors, contentPadding, content)

/** The same as [FilledTonalButton]: an outline is the one thing no button here wears. */
@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") shapes: ButtonShapes? = null,
    contentPadding: PaddingValues? = null,
    content: @Composable RowScope.() -> Unit,
) = PillButton(onClick, modifier, enabled, filled = false, colors = null, contentPadding, content)

/** A round icon in the veil. Takes Material's call shape, keeping its 40dp and its 48dp to aim at. */
@Composable
fun FilledIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) = CircleButton(
    onClick = onClick,
    modifier = modifier.minimumInteractiveComponentSize(),
    size = ICON_BUTTON_SIZE,
    enabled = enabled,
    content = content,
)

@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = RELEASE_SIDE_BUTTON,
    enabled: Boolean = true,
) {
    CircleButton(onClick = onClick, modifier = modifier, size = size, enabled = enabled) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(size * 0.44f),
        )
    }
}

private val TrackFill = Color(0xFF767680)
private const val DARK_TRACK_ALPHA = 0.24f
private const val LIGHT_TRACK_ALPHA = 0.12f
private val DarkPill = Color(0xFF636366)
private val TrackPadding = 4.dp
private val LabelPadding = 18.dp
private val LabelVerticalPadding = 9.dp
private val EdgeFade = 28.dp
private const val PILL_DAMPING = 0.8f

/**
 * One choice among a few, as a track with a sliding pill. Segments share the track evenly while every
 * label fits, take what their labels need while they fit together, and past that keep their own widths
 * and scroll, faded at whichever edge has more beyond it. A [selectedIndex] outside the options means
 * nothing is selected.
 */
@Composable
fun PillSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (options.isEmpty()) return
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val style = MaterialTheme.typography.labelMedium
    val measurer = rememberTextMeasurer()
    val light = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val trackColor = TrackFill.copy(alpha = if (light) LIGHT_TRACK_ALPHA else DARK_TRACK_ALPHA)
    val pillColor = if (light) Color.White else DarkPill
    val labelColor = if (light) Color.Black else Color.White
    val selected = selectedIndex.takeIf { it in options.indices }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(trackColor),
    ) {
        val inset = with(density) { TrackPadding.toPx() }
        val labelPadding = with(density) { LabelPadding.toPx() }
        val viewport = constraints.maxWidth.toFloat()
        val room = viewport - inset * 2f
        val natural = remember(options, style, measurer, density) {
            options.map { label ->
                measurer.measure(label, style, maxLines = 1, softWrap = false).size.width +
                    labelPadding * 2f
            }
        }
        val needed = natural.sum()
        val scrolls = needed > room
        val widths = when {
            scrolls -> natural
            natural.max() * options.size <= room -> List(options.size) { room / options.size }
            else -> natural.map { it + (room - needed) / options.size }
        }
        val starts = widths.runningFold(inset) { start, width -> start + width }
        val contentWidth = if (scrolls) needed + inset * 2f else viewport

        val motion: AnimationSpec<Float> = spring(PILL_DAMPING)
        val pillStart by animateFloatAsState(selected?.let(starts::get) ?: 0f, motion, label = "pillStart")
        val pillWidth by animateFloatAsState(selected?.let(widths::get) ?: 0f, motion, label = "pillWidth")

        val scroll = rememberScrollState()
        // The selection centred, as far as the ends allow: there at once the first time, slid after.
        val placed = remember { booleanArrayOf(false) }
        LaunchedEffect(selected, natural, viewport) {
            if (!scrolls || selected == null) return@LaunchedEffect
            val max = snapshotFlow { scroll.maxValue }.first { it > 0 }
            val centred = (starts[selected] + widths[selected] / 2f - viewport / 2f).roundToInt()
                .coerceIn(0, max)
            if (placed[0]) scroll.animateScrollTo(centred) else scroll.scrollTo(centred)
            placed[0] = true
        }

        Box(
            Modifier
                .fillMaxWidth()
                .then(
                    if (scrolls) {
                        val fadePx = with(density) { EdgeFade.toPx() }
                        Modifier
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                val start = (scroll.value / fadePx).coerceIn(0f, 1f)
                                val end = ((scroll.maxValue - scroll.value) / fadePx).coerceIn(0f, 1f)
                                val fade = fadePx / this.size.width
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        0f to Color.Black.copy(alpha = 1f - start),
                                        fade to Color.Black,
                                        1f - fade to Color.Black,
                                        1f to Color.Black.copy(alpha = 1f - end),
                                    ),
                                    blendMode = BlendMode.DstIn,
                                )
                            }
                            .horizontalScroll(scroll)
                    } else {
                        Modifier
                    }
                ),
        ) {
            Box(
                Modifier
                    .width(with(density) { contentWidth.toDp() })
                    .padding(vertical = TrackPadding),
            ) {
                if (selected != null) {
                    Box(
                        Modifier
                            .matchParentSize()
                            // Read here rather than in composition, so the slide recomposes nothing.
                            .layout { measurable, constraints ->
                                val width = pillWidth.roundToInt().coerceAtLeast(0)
                                val placeable = measurable.measure(
                                    Constraints.fixed(width, constraints.maxHeight)
                                )
                                layout(constraints.maxWidth, constraints.maxHeight) {
                                    placeable.place(pillStart.roundToInt(), 0)
                                }
                            }
                            .clip(CircleShape)
                            .background(pillColor),
                    )
                }
                options.forEachIndexed { index, label ->
                    Box(
                        modifier = Modifier
                            .atX(starts[index])
                            .width(with(density) { widths[index].toDp() })
                            .clickable(
                                enabled = enabled,
                                interactionSource = null,
                                indication = null,
                            ) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(index)
                            }
                            .semantics {
                                this.selected = index == selected
                                role = Role.Tab
                            }
                            .padding(vertical = LabelVerticalPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = style,
                            color = labelColor,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

private fun Modifier.atX(x: Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) { placeable.place(IntOffset(x.roundToInt(), 0)) }
}
