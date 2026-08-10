package ca.ilianokokoro.umihi.music.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import ca.ilianokokoro.umihi.music.core.domain.ArtistRef
import ca.ilianokokoro.umihi.music.core.domain.Track
import ca.ilianokokoro.umihi.music.core.domain.formatTrackDuration
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

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
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Jelyvo", style = MaterialTheme.typography.displayMedium)
                // Renders a :music-core value, so a green window also proves the shared
                // domain module is really on the desktop classpath.
                Text(
                    text = SMOKE_TRACK.let { "${it.title} — ${it.artist.name} · ${formatTrackDuration(it.durationMs)}" },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

private val SMOKE_TRACK = Track(
    id = "smoke",
    title = "Client macOS",
    artist = ArtistRef(name = "music-core"),
    durationMs = 215_000L,
)
