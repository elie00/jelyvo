package ca.ilianokokoro.umihi.music.ui.screens.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.SurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import ca.ilianokokoro.umihi.music.ui.components.studio.CircularProgressIndicator
import androidx.compose.material3.Icon
import ca.ilianokokoro.umihi.music.ui.components.studio.IconButton
import ca.ilianokokoro.umihi.music.ui.components.studio.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.managers.PlayerManager
import ca.ilianokokoro.umihi.music.extensions.toTimeString
import kotlinx.coroutines.delay

/**
 * 16:9 video surface shown in place of the artwork while the current item plays in Video mode.
 * The SurfaceView is attached to the session player through the MediaController (which forwards
 * it to the service's ExoPlayer) and detached on dispose, so audio-only screens never hold a
 * surface. Reuses the artwork's tap/double-tap/swipe gestures.
 */
@Composable
internal fun VideoSurface(
    onToggle: () -> Unit = {},
    onSkipNext: () -> Unit = {},
    onSkipPrevious: () -> Unit = {},
    onSeekForward: () -> Unit = {},
    onSeekBackward: () -> Unit = {},
    onEnterFullscreen: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val controller = PlayerManager.controllerState.collectAsStateWithLifecycle().value
    val isInPip = PlayerManager.isInPipMode.collectAsStateWithLifecycle().value
    var surfaceView by remember { mutableStateOf<SurfaceView?>(null) }

    Box(
        modifier = modifier.padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(MaterialTheme.shapes.large)
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { context -> SurfaceView(context).also { surfaceView = it } },
                modifier = Modifier
                    .fillMaxSize()
                    // Tap toggles lyrics; double-tap on the right/left half seeks ±10s.
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onToggle() },
                            onDoubleTap = { offset ->
                                if (offset.x >= size.width / 2f) {
                                    onSeekForward()
                                } else {
                                    onSeekBackward()
                                }
                            }
                        )
                    }
                    // Horizontal swipe: left = next track, right = previous track.
                    .pointerInput(Unit) {
                        var dragTotal = 0f
                        val threshold = size.width * 0.15f
                        detectHorizontalDragGestures(
                            onDragStart = { dragTotal = 0f },
                            onDragEnd = {
                                if (dragTotal <= -threshold) {
                                    onSkipNext()
                                } else if (dragTotal >= threshold) {
                                    onSkipPrevious()
                                }
                            },
                            onHorizontalDrag = { _, dragAmount -> dragTotal += dragAmount }
                        )
                    }
            )

            IconButton(
                onClick = onEnterFullscreen,
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Fullscreen,
                    contentDescription = stringResource(R.string.fullscreen),
                    tint = Color.White
                )
            }
        }
    }

    // Keyed on isInPip too: leaving PiP re-runs the effect so this surface is re-attached after
    // the PiP overlay's surface cleared itself (clearing is a no-op for a non-current view).
    DisposableEffect(controller, surfaceView, isInPip) {
        val view = surfaceView
        if (controller != null && view != null && !isInPip) {
            controller.setVideoSurfaceView(view)
        }
        onDispose {
            if (view != null) {
                controller?.clearVideoSurfaceView(view)
            }
        }
    }
}

/**
 * Fullscreen Video mode: the whole screen is black with the video letterboxed at its largest
 * 16:9 fit, all player chrome replaced by overlay controls (back, ±10s, play/pause, seek bar)
 * that auto-hide after a short idle delay while playing. Tap toggles the overlay; double-tap on
 * the right/left half seeks ±10s like the regular surface.
 */
@Composable
internal fun FullscreenVideo(
    uiState: PlayerState,
    onBack: () -> Unit,
    onSeek: (location: Float) -> Unit,
    onSeekPlayer: () -> Unit,
    onUpdateSeekBarHeldState: (isHeld: Boolean) -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val controller = PlayerManager.controllerState.collectAsStateWithLifecycle().value
    val isInPip = PlayerManager.isInPipMode.collectAsStateWithLifecycle().value
    var surfaceView by remember { mutableStateOf<SurfaceView?>(null) }

    var areControlsShown by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableIntStateOf(0) }

    fun showControls() {
        areControlsShown = true
        interactionTick++
    }

    // Auto-hide the overlay after an idle delay, but only while actually playing and while the
    // seek bar isn't held; any interaction bumps [interactionTick] to restart the countdown.
    LaunchedEffect(interactionTick, uiState.isPlaying, uiState.isSeekBarHeld) {
        if (uiState.isPlaying && !uiState.isSeekBarHeld) {
            delay(Constants.Player.FULLSCREEN_CONTROLS_HIDE_DELAY_MS)
            areControlsShown = false
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (areControlsShown) {
                            areControlsShown = false
                        } else {
                            showControls()
                        }
                    },
                    onDoubleTap = { offset ->
                        if (offset.x >= size.width / 2f) {
                            onSeekForward()
                        } else {
                            onSeekBackward()
                        }
                        showControls()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { context -> SurfaceView(context).also { surfaceView = it } },
            modifier = Modifier.aspectRatio(16f / 9f)
        )

        AnimatedVisibility(
            visible = areControlsShown,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            FullscreenVideoControls(
                uiState = uiState,
                onBack = onBack,
                onSeek = onSeek,
                onSeekPlayer = onSeekPlayer,
                onUpdateSeekBarHeldState = onUpdateSeekBarHeldState,
                onSeekForward = {
                    onSeekForward()
                    showControls()
                },
                onSeekBackward = {
                    onSeekBackward()
                    showControls()
                },
            )
        }
    }

    DisposableEffect(controller, surfaceView, isInPip) {
        val view = surfaceView
        if (controller != null && view != null && !isInPip) {
            controller.setVideoSurfaceView(view)
        }
        onDispose {
            if (view != null) {
                controller?.clearVideoSurfaceView(view)
            }
        }
    }
}

@Composable
private fun FullscreenVideoControls(
    uiState: PlayerState,
    onBack: () -> Unit,
    onSeek: (location: Float) -> Unit,
    onSeekPlayer: () -> Unit,
    onUpdateSeekBarHeldState: (isHeld: Boolean) -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val seekEnabled = !uiState.isLoading && uiState.playbackProgress.duration > 0f
    val safeDuration = uiState.playbackProgress.duration.coerceAtLeast(1f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = Color.White
            )
        }

        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onSeekBackward) {
                Icon(
                    imageVector = Icons.Rounded.Replay10,
                    contentDescription = stringResource(R.string.seek_backward_10),
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    val controller = PlayerManager.currentController
                    if (controller?.isPlaying == true) {
                        controller.pause()
                    } else {
                        controller?.play()
                    }
                },
                modifier = Modifier.size(72.dp)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(40.dp))
                } else {
                    Icon(
                        imageVector = if (uiState.isPlaying) {
                            Icons.Rounded.Pause
                        } else {
                            Icons.Rounded.PlayArrow
                        },
                        contentDescription = if (uiState.isPlaying) {
                            stringResource(R.string.pause)
                        } else {
                            stringResource(R.string.play)
                        },
                        tint = Color.White,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }

            IconButton(onClick = onSeekForward) {
                Icon(
                    imageVector = Icons.Rounded.Forward10,
                    contentDescription = stringResource(R.string.seek_forward_10),
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            val seekBarPosition = uiState.playbackProgress.position.coerceIn(0f, safeDuration)
            val seekBarState = remember(safeDuration) {
                SliderState(seekBarPosition, trackRange = 0f..safeDuration)
            }
            seekBarState.value = seekBarPosition
            Slider(
                state = seekBarState,
                enabled = seekEnabled,
                onValueChange = { newValue ->
                    onUpdateSeekBarHeldState(true)
                    onSeek(newValue)
                },
                onValueChangeFinished = {
                    haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                    onSeekPlayer()
                    onUpdateSeekBarHeldState(false)
                },
                thumb = {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                if (seekEnabled) {
                                    Color.White
                                } else {
                                    Color.White.copy(alpha = 0.38f)
                                }
                            )
                    )
                },
                track = { sliderState ->
                    LinearProgressIndicator(
                        progress = { sliderState.coercedValueAsFraction },
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = uiState.playbackProgress.position.toTimeString(),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum",
                    ),
                    color = Color.White
                )
                Text(
                    text = uiState.playbackProgress.duration.toTimeString(),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum",
                    ),
                    color = Color.White
                )
            }
        }
    }
}

/** Unwraps a Compose [Context] back to its hosting [Activity], if any. */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
