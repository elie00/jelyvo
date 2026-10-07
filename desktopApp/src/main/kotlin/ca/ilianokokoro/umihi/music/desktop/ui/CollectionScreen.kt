package ca.ilianokokoro.umihi.music.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.core.domain.Track
import ca.ilianokokoro.umihi.music.ui.components.SquareArtwork

/** Header size for an album or artist page, mirroring the phone's large cover. */
private val HEADER_ARTWORK = 180.dp

/**
 * Album and artist pages share one layout: a header that names the collection, then its tracks.
 * The phone separates context from content the same way once the window is wide enough.
 */
@Composable
fun CollectionScreen(
    title: String,
    subtitle: String,
    artworkUri: String,
    tracks: List<Track>,
    loading: Boolean,
    error: String?,
    playingTrackId: String?,
    onBack: () -> Unit,
    onPlayTrack: (Int) -> Unit,
    onEnqueue: (Track) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TextButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp, top = 8.dp)) {
            Text("← Retour")
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SquareArtwork(
                uri = artworkUri,
                modifier = Modifier.size(HEADER_ARTWORK),
                cornerRadius = 16.dp,
                requestSize = HEADER_ARTWORK,
            )
            Spacer(Modifier.width(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp),
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                tracks.isEmpty() -> Text(
                    text = "Aucun titre",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(tracks, key = { index, track -> "${track.id}#$index" }) { index, track ->
                        TrackRow(
                            track = track,
                            playing = playingTrackId == track.id,
                            onClick = { onPlayTrack(index) },
                            onEnqueue = { onEnqueue(track) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
