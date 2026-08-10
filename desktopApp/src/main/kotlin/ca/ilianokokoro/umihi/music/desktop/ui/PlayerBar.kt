package ca.ilianokokoro.umihi.music.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.core.domain.PlaybackProgress
import ca.ilianokokoro.umihi.music.core.domain.PlaybackStatus
import ca.ilianokokoro.umihi.music.core.domain.formatTrackDuration
import ca.ilianokokoro.umihi.music.desktop.playback.JavaFxAudioEngine
import ca.ilianokokoro.umihi.music.desktop.theme.TransportIcons
import ca.ilianokokoro.umihi.music.ui.components.SquareArtwork
import kotlinx.coroutines.delay

@Composable
fun PlayerBar(engine: JavaFxAudioEngine) {
    val state by engine.state.collectAsState()
    val queue by engine.queue.collectAsState()
    var progress by remember { mutableStateOf<PlaybackProgress?>(null) }
    var scrubbing by remember { mutableStateOf<Float?>(null) }

    // JavaFX exposes the playhead by polling, not by a flow.
    LaunchedEffect(state.currentTrack?.id) {
        while (true) {
            progress = engine.snapshotProgress()
            delay(500)
        }
    }

    val track = state.currentTrack
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            SquareArtwork(
                uri = track?.displayArtwork.orEmpty(),
                modifier = Modifier.size(ARTWORK_SIZE),
                cornerRadius = 8.dp,
                requestSize = ARTWORK_SIZE,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track?.title ?: "Rien en lecture",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when {
                        track == null -> ""
                        state.status == PlaybackStatus.BUFFERING -> "Résolution du flux…"
                        else -> track.artist.name
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = queuePositionLabel(state.currentIndex, queue.entries.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = engine::skipPrevious, enabled = track != null) {
                Icon(TransportIcons.SkipPrevious, contentDescription = "Titre précédent")
            }
            FilledIconButton(
                onClick = { if (state.isPlaying) engine.pause() else engine.play() },
                enabled = track != null,
            ) {
                Icon(
                    imageVector = if (state.isPlaying) TransportIcons.Pause else TransportIcons.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Lecture",
                )
            }
            IconButton(onClick = engine::skipNext, enabled = track != null) {
                Icon(TransportIcons.SkipNext, contentDescription = "Titre suivant")
            }
        }

        val duration = progress?.durationMs ?: 0L
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = formatTrackDuration(
                    scrubbing?.let { (it * duration).toLong() } ?: progress?.positionMs,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = scrubbing ?: progress?.fraction ?: 0f,
                onValueChange = { scrubbing = it },
                onValueChangeFinished = {
                    scrubbing?.let { engine.seekTo((it * duration).toLong()) }
                    scrubbing = null
                },
                enabled = track != null && duration > 0L,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatTrackDuration(duration.takeIf { it > 0L }),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "3 / 20" while a queue is loaded, empty otherwise. */
private fun queuePositionLabel(index: Int, size: Int): String =
    if (index >= 0 && size > 0) "${index + 1} / $size" else ""
