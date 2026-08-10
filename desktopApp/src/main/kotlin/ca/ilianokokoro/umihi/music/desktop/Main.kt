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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
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
import ca.ilianokokoro.umihi.music.core.domain.PlaybackStatus
import ca.ilianokokoro.umihi.music.core.domain.Track
import ca.ilianokokoro.umihi.music.core.domain.formatTrackDuration
import ca.ilianokokoro.umihi.music.core.youtube.newpipe.NewPipeMusicSearch
import ca.ilianokokoro.umihi.music.core.youtube.newpipe.NewPipeStreamResolver
import ca.ilianokokoro.umihi.music.desktop.playback.JavaFxAudioEngine
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
    val engine = remember { JavaFxAudioEngine(NewPipeStreamResolver()) }
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

    MaterialTheme(colorScheme = darkColorScheme()) {
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
                            items(results, key = { it.id }) { track ->
                                TrackRow(
                                    track = track,
                                    playing = state.currentTrack?.id == track.id,
                                    onClick = { scope.launch { engine.playNow(track) } },
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
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
    var positionLabel by remember { mutableStateOf("") }

    // JavaFX exposes the playhead by polling, not by a flow.
    LaunchedEffect(state.currentTrack?.id) {
        while (true) {
            positionLabel = engine.progress()
                ?.let { "${formatTrackDuration(it.positionMs)} / ${formatTrackDuration(it.durationMs)}" }
                .orEmpty()
            delay(500)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val track = state.currentTrack
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
                    else -> "${track.artist.name} · $positionLabel"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Button(onClick = { engine.seekBy(-10_000) }, enabled = track != null) { Text("−10 s") }
        Button(
            onClick = { if (state.isPlaying) engine.pause() else engine.play() },
            enabled = track != null,
        ) {
            Text(if (state.isPlaying) "Pause" else "Lecture")
        }
        Button(onClick = { engine.seekBy(10_000) }, enabled = track != null) { Text("+10 s") }
    }
}
