package ca.ilianokokoro.umihi.music.ui.components.miniplayer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.managers.PlayerManager
import ca.ilianokokoro.umihi.music.extensions.toSong
import ca.ilianokokoro.umihi.music.ui.components.songFromDragLocalState
import ca.ilianokokoro.umihi.music.ui.theme.ArtworkPlayerTheme
import kotlinx.coroutines.delay

@Composable
fun MiniPlayerWrapper(
    modifier: Modifier = Modifier,
    artworkModifier: Modifier = Modifier,
    onMiniPlayerPressed: () -> Unit,
    showMiniPlayer: Boolean,
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val player by PlayerManager.controllerState.collectAsStateWithLifecycle()
    var currentSong by remember { mutableStateOf(player?.currentMediaItem?.toSong()) }
    var songIsPlaying by remember(player) {
        mutableStateOf(player?.isPlaying)
    }
    var songIsLoading by remember(player) {
        mutableStateOf(player?.playbackState == Player.STATE_BUFFERING)
    }
    var progressFraction by remember(player) {
        mutableFloatStateOf(0f)
    }
    var isQueueDropHovered by remember { mutableStateOf(false) }
    val queueDropTarget = remember(context, hapticFeedback) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                isQueueDropHovered = true
                hapticFeedback.performHapticFeedback(
                    HapticFeedbackType.GestureThresholdActivate
                )
            }

            override fun onExited(event: DragAndDropEvent) {
                isQueueDropHovered = false
            }

            override fun onEnded(event: DragAndDropEvent) {
                isQueueDropHovered = false
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                val song = songFromDragLocalState(
                    event.toAndroidDragEvent().localState
                ) ?: return false

                isQueueDropHovered = false
                PlayerManager.addToQueue(song, context)
                hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                return true
            }
        }
    }

    val bottomInsetPx: Int = with(LocalDensity.current) {
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().roundToPx()
    }

    DisposableEffect(player) {
        currentSong = player?.currentMediaItem?.toSong()
        songIsPlaying = player?.isPlaying
        songIsLoading = player?.playbackState == Player.STATE_BUFFERING

        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentSong = mediaItem?.toSong()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                songIsPlaying = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        songIsLoading = true
                    }

                    Player.STATE_READY -> {
                        songIsLoading = false
                    }

                    Player.STATE_IDLE,
                    Player.STATE_ENDED -> {
                        songIsLoading = false
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                songIsLoading = false
                songIsPlaying = false
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }

    // A lightweight ticker keeps Jelyvo's signature pulse accurate without involving the
    // full-player ViewModel. It stops whenever playback pauses or the dock is hidden.
    LaunchedEffect(player, currentSong?.youtubeId, songIsPlaying, showMiniPlayer) {
        while (true) {
            val duration = player?.duration ?: 0L
            progressFraction = if (duration > 0L) {
                ((player?.currentPosition ?: 0L).toFloat() / duration.toFloat())
                    .coerceIn(0f, 1f)
            } else {
                0f
            }

            if (songIsPlaying != true || !showMiniPlayer) break
            delay(500)
        }
    }

    AnimatedVisibility(
        visible = currentSong != null && showMiniPlayer,
        enter = slideInVertically(initialOffsetY = { it + bottomInsetPx }),
        exit = slideOutVertically(targetOffsetY = { it + bottomInsetPx }),
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .height(Constants.Ui.MiniPlayer.HEIGHT)
    ) {
        val song = currentSong ?: return@AnimatedVisibility
        ArtworkPlayerTheme(
            artworkUri = song.thumbnailPath ?: song.thumbnailHref,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .dragAndDropTarget(
                        shouldStartDragAndDrop = { event ->
                            songFromDragLocalState(
                                event.toAndroidDragEvent().localState
                            ) != null
                        },
                        target = queueDropTarget,
                    ),
            ) {
                MiniPlayer(
                    currentSong = song,
                    artworkModifier = artworkModifier,
                    onClick = onMiniPlayerPressed,
                    onPlayPause = {
                        if (player?.isPlaying == true) {
                            player?.pause()
                        } else {
                            player?.play()
                        }
                    },
                    onSkipNext = { player?.seekToNext() },
                    onSkipPrevious = { player?.seekToPrevious() },
                    isPlaying = songIsPlaying == true,
                    isLoading = songIsLoading,
                    progressFraction = progressFraction,
                )

                AnimatedVisibility(
                    visible = isQueueDropHovered,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.matchParentSize(),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        tonalElevation = 6.dp,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(
                                10.dp,
                                Alignment.CenterHorizontally,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                                contentDescription = null,
                            )
                            Text(
                                text = stringResource(R.string.drop_to_queue),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}
