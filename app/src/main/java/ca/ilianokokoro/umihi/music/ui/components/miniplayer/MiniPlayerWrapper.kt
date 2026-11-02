package ca.ilianokokoro.umihi.music.ui.components.miniplayer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import android.widget.Toast
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.domain.PlaybackEngine
import ca.ilianokokoro.umihi.music.core.domain.PlaybackStatus
import ca.ilianokokoro.umihi.music.models.adapters.toTrack
import ca.ilianokokoro.umihi.music.ui.components.songDragAndDropTarget
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay

@Composable
fun MiniPlayerWrapper(
    playbackEngine: PlaybackEngine,
    modifier: Modifier = Modifier,
    artworkModifier: Modifier = Modifier,
    onMiniPlayerPressed: () -> Unit,
    showMiniPlayer: Boolean,
    hazeState: HazeState,
) {
    val playbackState by playbackEngine.state.collectAsStateWithLifecycle()
    val currentTrack = playbackState.currentTrack
    val isPlaying = playbackState.isPlaying
    val isLoading = playbackState.status == PlaybackStatus.BUFFERING
    var progressFraction by remember { mutableFloatStateOf(0f) }
    var isQueueDropHovered by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val queuedMessage = stringResource(R.string.added_queue_toast)

    val bottomInsetPx: Int = with(LocalDensity.current) {
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().roundToPx()
    }

    // A lightweight ticker keeps Jelyvo's signature pulse accurate without involving the
    // full-player ViewModel. It stops whenever playback pauses or the dock is hidden.
    LaunchedEffect(playbackEngine, currentTrack?.id, isPlaying, showMiniPlayer) {
        while (true) {
            progressFraction = playbackEngine.progress()?.fraction ?: 0f

            if (!isPlaying || !showMiniPlayer) break
            delay(500)
        }
    }

    AnimatedVisibility(
        visible = currentTrack != null && showMiniPlayer,
        enter = slideInVertically(initialOffsetY = { it + bottomInsetPx }),
        exit = slideOutVertically(targetOffsetY = { it + bottomInsetPx }),
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .height(Constants.Ui.MiniPlayer.HEIGHT)
    ) {
        val track = currentTrack ?: return@AnimatedVisibility
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .songDragAndDropTarget(
                    onSongDropped = { droppedSong ->
                        playbackEngine.enqueue(droppedSong.toTrack())
                        Toast.makeText(context, queuedMessage, Toast.LENGTH_SHORT).show()
                        true
                    },
                    onHoverChanged = { isQueueDropHovered = it },
                ),
        ) {
            MiniPlayer(
                hazeState = hazeState,
                currentTrack = track,
                artworkModifier = artworkModifier,
                onClick = onMiniPlayerPressed,
                onPlayPause = {
                    if (isPlaying) {
                        playbackEngine.pause()
                    } else {
                        playbackEngine.play()
                    }
                },
                onSkipNext = playbackEngine::skipNext,
                onSkipPrevious = playbackEngine::skipPrevious,
                isPlaying = isPlaying,
                isLoading = isLoading,
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
                    shape = RoundedCornerShape(percent = 50),
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
