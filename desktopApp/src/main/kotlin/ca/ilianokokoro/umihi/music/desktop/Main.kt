package ca.ilianokokoro.umihi.music.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import ca.ilianokokoro.umihi.music.core.domain.PlaybackProgress
import ca.ilianokokoro.umihi.music.core.domain.PlaybackQueue
import ca.ilianokokoro.umihi.music.core.domain.PlaybackStatus
import ca.ilianokokoro.umihi.music.core.domain.QueueEntry
import ca.ilianokokoro.umihi.music.core.domain.Track
import ca.ilianokokoro.umihi.music.core.domain.formatTrackDuration
import ca.ilianokokoro.umihi.music.core.youtube.newpipe.NewPipeMusicSearch
import ca.ilianokokoro.umihi.music.core.youtube.newpipe.NewPipeStreamResolver
import ca.ilianokokoro.umihi.music.desktop.playback.JavaFxAudioEngine
import androidx.compose.foundation.layout.Spacer
import ca.ilianokokoro.umihi.music.desktop.theme.TransportIcons
import ca.ilianokokoro.umihi.music.desktop.theme.nunitoFontFamily
import ca.ilianokokoro.umihi.music.ui.components.SquareArtwork
import ca.ilianokokoro.umihi.music.ui.theme.UmihiMusicTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Jelyvo",
        state = rememberWindowState(size = DpSize(1100.dp, 720.dp)),
    ) {
        JelyvoDesktopApp()
    }
}

@Composable
private fun JelyvoDesktopApp() {
    val engine = remember { JavaFxAudioEngine(NewPipeStreamResolver(preferMuxedProgressive = true)) }
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<Track>()) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val state by engine.state.collectAsState()

    fun runSearch() {
        if (query.isBlank()) return
        scope.launch {
            searching = true
            error = null
            runCatching { NewPipeMusicSearch.search(query) }
                .onSuccess { results = it }
                .onFailure { error = it.message ?: it::class.simpleName }
            searching = false
        }
    }

    UmihiMusicTheme(darkTheme = true, fontFamily = nunitoFontFamily) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Rechercher sur YouTube Music") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = ::runSearch, enabled = !searching) { Text("Chercher") }
                }

                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (searching) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            itemsIndexed(results, key = { _, track -> track.id }) { index, track ->
                                TrackRow(
                                    track = track,
                                    playing = state.currentTrack?.id == track.id,
                                    onClick = { scope.launch { engine.load(queueOf(results), index) } },
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }

                HorizontalDivider()
                TransportBar(engine = engine)
            }
        }
    }
}

@Composable
private fun TrackRow(track: Track, playing: Boolean, onClick: () -> Unit) {
    val background = if (playing) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SquareArtwork(
            uri = track.displayArtwork.orEmpty(),
            modifier = Modifier.size(ARTWORK_SIZE),
            cornerRadius = 8.dp,
            requestSize = ARTWORK_SIZE,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = formatTrackDuration(track.durationMs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TransportBar(engine: JavaFxAudioEngine) {
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
                text = formatTrackDuration(scrubbing?.let { (it * duration).toLong() } ?: progress?.positionMs),
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

/** The search results become the queue, so skipping works the way it does on the phone. */
private fun queueOf(tracks: List<Track>): PlaybackQueue = PlaybackQueue(
    entries = tracks.mapIndexed { index, track ->
        QueueEntry(entryId = "${track.id}#$index", track = track)
    },
)

/** Same 60 dp row thumbnail the phone draws. */
private val ARTWORK_SIZE = 60.dp
