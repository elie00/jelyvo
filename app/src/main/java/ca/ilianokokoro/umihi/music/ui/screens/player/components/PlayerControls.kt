package ca.ilianokokoro.umihi.music.ui.screens.player.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.helpers.ComposeHelper
import ca.ilianokokoro.umihi.music.core.helpers.UmihiHelper.speedLabel
import ca.ilianokokoro.umihi.music.core.managers.PlayerManager
import ca.ilianokokoro.umihi.music.extensions.toTimeString
import ca.ilianokokoro.umihi.music.models.PlaybackAudioInfo
import ca.ilianokokoro.umihi.music.ui.screens.player.PlaybackProgress

private val PlayPauseButtonSize = 76.dp
private val SkipButtonSize = 54.dp
private val SecondaryButtonSize = 44.dp
private val SecondaryIconSize = 24.dp

@Composable
fun PlayerControls(
    modifier: Modifier = Modifier,
    isPlaying: Boolean,
    isLoading: Boolean,
    progress: PlaybackProgress,
    onSeekPlayer: () -> Unit,
    onUpdateSeekBarHeldState: (isHeld: Boolean) -> Unit,
    onSeek: (location: Float) -> Unit,
    onOpenQueue: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenSpeedSelector: () -> Unit,
    playbackSpeed: Float,
    sleepTimerRemainingSeconds: Long?,
    showQueueButton: Boolean = true,
    isCasting: Boolean = false,
    outputDeviceName: String? = null,
    audioInfo: PlaybackAudioInfo = PlaybackAudioInfo(),
) {
    val hapticFeedback = LocalHapticFeedback.current
    val player by PlayerManager.controllerState.collectAsStateWithLifecycle()
    val repeatMode = ComposeHelper.rememberRepeatMode(player)
    val shuffleEnabled = ComposeHelper.rememberShuffleMode(player)
    val seekEnabled = !isLoading && progress.duration > 0f
    val safeDuration = progress.duration.coerceAtLeast(1f)

    Column(
        modifier = modifier
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Slider(
            value = progress.position.coerceIn(0f, safeDuration),
            valueRange = 0f..safeDuration,
            enabled = seekEnabled,

            onValueChange = { newValue ->
                onUpdateSeekBarHeldState(true)
                onSeek(newValue)
            },

            onValueChangeFinished = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                onSeekPlayer()
                onUpdateSeekBarHeldState(false)
            },

            thumb = {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(
                            if (seekEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f)
                            }
                        )
                )
            },

            track = { sliderState ->
                LinearWavyProgressIndicator(
                    progress = { sliderState.coercedValueAsFraction },
                    modifier = Modifier.fillMaxWidth(),
                    // Flat track while paused, wavy while playing
                    amplitude = { if (isPlaying && seekEnabled) 1f else 0f },
                )
            },

            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = progress.position.toTimeString(),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum",
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = progress.duration.toTimeString(),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum",
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            PlaybackAudioInfoPill(
                info = audioInfo,
                labelOverride = if (!outputDeviceName.isNullOrBlank()) {
                    stringResource(
                        R.string.cast_connected_to,
                        outputDeviceName,
                    )
                } else {
                    null
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 58.dp),
            )
        }

        // Main transport row: Previous - Play/Pause - Next
        Row(
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(
                20.dp,
                Alignment.CenterHorizontally
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SkipButton(
                icon = Icons.Rounded.SkipPrevious,
                contentDescription = stringResource(R.string.previous),
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                    PlayerManager.skipToPrevious()
                },
            )

            PlayPauseMorphButton(
                isPlaying = isPlaying,
                isLoading = isLoading,
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    if (isPlaying) {
                        PlayerManager.currentController?.pause()
                    } else {
                        PlayerManager.currentController?.play()
                    }
                },
            )

            SkipButton(
                icon = Icons.Rounded.SkipNext,
                contentDescription = stringResource(R.string.next),
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                    PlayerManager.skipToNext()
                },
            )
        }

        // Secondary controls: ghost icon buttons, active states get a pill background
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Speed
                GhostToggleButton(
                    checked = playbackSpeed != 1.0f,
                    enabled = !isCasting,
                    onCheckedChange = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onOpenSpeedSelector()
                    },
                    icon = Icons.Rounded.Speed,
                    contentDescription = stringResource(R.string.playback_speed),
                    statusText = playbackSpeed
                        .takeIf { it != 1.0f }
                        ?.speedLabel(),
                )

                // Sleep Timer
                val sleepTimerStatus = sleepTimerRemainingSeconds
                    ?.coerceAtLeast(0L)
                    ?.let(::formatTimerStatus)
                GhostToggleButton(
                    checked = sleepTimerRemainingSeconds != null,
                    onCheckedChange = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onOpenSleepTimer()
                    },
                    icon = Icons.Rounded.Timer,
                    contentDescription = stringResource(R.string.sleep_timer),
                    statusText = sleepTimerStatus,
                    stateDescription = sleepTimerStatus?.let { remaining ->
                        "$remaining, ${stringResource(R.string.sleep_timer_remaining)}"
                    },
                )

                if (showQueueButton) {
                    // Queue
                    IconButton(
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                            onOpenQueue()
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(SecondaryButtonSize),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                            contentDescription = stringResource(R.string.queue),
                            modifier = Modifier.size(SecondaryIconSize)
                        )
                    }
                }

                // Shuffle
                GhostToggleButton(
                    checked = shuffleEnabled,
                    enabled = !isCasting,
                    onCheckedChange = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.ToggleOn)
                        PlayerManager.toggleShuffleMode()
                    },
                    icon = Icons.Rounded.Shuffle,
                    contentDescription = stringResource(R.string.shuffle),
                )

                // Repeat
                GhostToggleButton(
                    checked = repeatMode == Player.REPEAT_MODE_ALL || repeatMode == Player.REPEAT_MODE_ONE,
                    onCheckedChange = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.ToggleOn)
                        PlayerManager.cycleRepeatMode()
                    },
                    icon = when (repeatMode) {
                        Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOne
                        else -> Icons.Rounded.Repeat
                    },
                    contentDescription = stringResource(R.string.repeat),
                    showDotIndicator = repeatMode == Player.REPEAT_MODE_ONE,
                )
            }
        }
    }
}

/**
 * Central play/pause button whose container morphs between a circle (paused)
 * and a rounded square (playing) by animating the corner radius.
 */
@Composable
private fun PlayPauseMorphButton(
    isPlaying: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = if (isPressed) 90 else 140),
        label = "playPausePressScale",
    )
    val cornerRadius: Dp by animateDpAsState(
        targetValue = if (isPlaying && !isLoading) {
            22.dp
        } else {
            PlayPauseButtonSize / 2
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "playPauseCornerRadius"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(cornerRadius),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        interactionSource = interactionSource,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(PlayPauseButtonSize)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isLoading) {
                CircularWavyProgressIndicator(
                    modifier = Modifier.size(28.dp),
                )
            } else {
                val icon = if (isPlaying) {
                    Icons.Rounded.Pause
                } else {
                    Icons.Rounded.PlayArrow
                }
                Icon(
                    imageVector = icon,
                    contentDescription = if (isPlaying) {
                        stringResource(R.string.pause)
                    } else {
                        stringResource(R.string.play)
                    },
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    }
}

/**
 * Previous/Next skip button: tonal container tinted by the animated artwork
 * color scheme, no outline.
 */
@Composable
private fun SkipButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalIconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(SkipButtonSize),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(30.dp)
        )
    }
}

/**
 * Ghost icon toggle button for the secondary controls row. Inactive: no
 * container. Active: secondaryContainer pill background. Optionally shows a
 * small dot indicator under the icon (repeat-one).
 */
@Composable
private fun GhostToggleButton(
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    showDotIndicator: Boolean = false,
    statusText: String? = null,
    stateDescription: String? = statusText,
) {
    IconToggleButton(
        checked = checked,
        enabled = enabled,
        onCheckedChange = onCheckedChange,
        colors = IconButtonDefaults.iconToggleButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(SecondaryButtonSize)
            .clip(RoundedCornerShape(percent = 50))
            .semantics {
                stateDescription?.let { this.stateDescription = it }
            },
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier
                    .offset(y = if (statusText == null) 0.dp else (-4).dp)
                    .size(if (statusText == null) SecondaryIconSize else 20.dp)
            )
            statusText?.let { status ->
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 8.sp,
                        lineHeight = 8.sp,
                        fontFeatureSettings = "tnum",
                    ),
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 4.dp),
                )
            }
            if (showDotIndicator) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = 6.dp)
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(LocalContentColor.current),
                )
            }
        }
    }
}

private fun formatTimerStatus(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
