/*
 * Layout and gesture rules adapted from BitChord's MiniPlayer
 * (https://github.com/kushagrasinghx/BitChord), GPL-3.0.
 * Copyright (C) Kushagra Singh and BitChord contributors.
 */
package ca.ilianokokoro.umihi.music.ui.components.miniplayer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.domain.Track
import ca.ilianokokoro.umihi.music.ui.components.SquareImage
import ca.ilianokokoro.umihi.music.ui.components.frosted
import dev.chrisbanes.haze.HazeState
import kotlin.math.abs

/** Which way a drag on the bar is heading once it passes the touch slop. */
internal enum class MiniPlayerDrag { Sideways, Pull, Ignored }

/**
 * A thumb swiping up from the bottom edge travels on an arc. Judged level, many real pulls would
 * come out sideways, so a drag may go further sideways than up and still be a pull.
 */
private const val PULL_BIAS = 1.5f

internal fun classifyMiniPlayerDrag(dx: Float, dy: Float): MiniPlayerDrag = when {
    dy < 0f && abs(dx) < -dy * PULL_BIAS -> MiniPlayerDrag.Pull
    abs(dx) < abs(dy) -> MiniPlayerDrag.Ignored
    else -> MiniPlayerDrag.Sideways
}

private val TRACK_SWIPE_THRESHOLD = 72.dp
private val PULL_THRESHOLD = 40.dp

/**
 * Both drags on the bar: sideways through the queue, and up into the player. One detector rather
 * than one per axis, so a gesture is decided once. Acting on release means one long gesture never
 * skips more than one track, and a tap is left to the bar's own clickable.
 */
@Composable
private fun Modifier.miniPlayerGestures(
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpen: () -> Unit,
): Modifier {
    val haptic = LocalHapticFeedback.current
    val currentOnNext by rememberUpdatedState(onNext)
    val currentOnPrevious by rememberUpdatedState(onPrevious)
    val currentOnOpen by rememberUpdatedState(onOpen)
    return pointerInput(Unit) {
        val swipeThreshold = TRACK_SWIPE_THRESHOLD.toPx()
        val pullThreshold = PULL_THRESHOLD.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var start: PointerInputChange? = null
            var heading = MiniPlayerDrag.Ignored
            while (start == null) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                    ?: return@awaitEachGesture
                if (!change.pressed || change.isConsumed) return@awaitEachGesture
                val moved = change.position - down.position
                if (moved.getDistance() < viewConfiguration.touchSlop) continue
                heading = classifyMiniPlayerDrag(moved.x, moved.y)
                if (heading == MiniPlayerDrag.Ignored) return@awaitEachGesture
                change.consume()
                start = change
            }
            var draggedX = start.position.x - down.position.x
            var draggedY = start.position.y - down.position.y
            val finished = drag(down.id) { change ->
                // Read before consuming: a consumed change reports no movement.
                draggedX += change.positionChange().x
                draggedY += change.positionChange().y
                change.consume()
            }
            if (!finished) return@awaitEachGesture
            val action = when {
                heading == MiniPlayerDrag.Pull && -draggedY >= pullThreshold -> currentOnOpen
                heading != MiniPlayerDrag.Sideways -> null
                draggedX <= -swipeThreshold -> currentOnNext
                draggedX >= swipeThreshold -> currentOnPrevious
                else -> null
            }
            action?.let {
                haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                it()
            }
        }
    }
}

/**
 * The bar above the tabs: a frosted pill with the artwork, the track, and bare controls. Tap or
 * pull up to open the player, swipe sideways to change track.
 *
 * @param hazeState captures the content scrolling under the bar.
 */
@Composable
fun MiniPlayer(
    currentTrack: Track,
    onClick: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    isPlaying: Boolean,
    isLoading: Boolean,
    progressFraction: Float,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    artworkModifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(percent = 50)
    val colors = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .fillMaxWidth()
            .miniPlayerGestures(
                onNext = onSkipNext,
                onPrevious = onSkipPrevious,
                onOpen = onClick,
            )
            .clip(shape)
            .frosted(hazeState)
            .border(0.5.dp, colors.outlineVariant, shape)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedContent(
                targetState = currentTrack,
                contentKey = { it.id },
                transitionSpec = {
                    (fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 8 }) togetherWith
                        fadeOut(tween(90))
                },
                label = "miniPlayerTrack",
                modifier = Modifier.weight(1f),
            ) { track ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SquareImage(
                        uri = track.displayArtwork.orEmpty(),
                        modifier = artworkModifier.size(40.dp),
                        cornerRadius = 8.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.basicMarquee(),
                        )
                        Text(
                            text = track.artist.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            IconButton(onClick = onSkipPrevious, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = stringResource(R.string.previous),
                    tint = colors.onBackground,
                    modifier = Modifier.size(32.dp),
                )
            }
            if (isLoading) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = colors.onBackground,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp),
                    )
                }
            } else {
                IconButton(onClick = onPlayPause, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(
                            if (isPlaying) R.string.pause else R.string.play
                        ),
                        tint = colors.onBackground,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onSkipNext, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = stringResource(R.string.next),
                    tint = colors.onBackground,
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        // BitChord's bar has no progress. Jelyvo's keeps a hairline, at the pill's foot.
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(2.dp)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(colors.onBackground.copy(alpha = 0.55f))
            )
        }
    }
}
