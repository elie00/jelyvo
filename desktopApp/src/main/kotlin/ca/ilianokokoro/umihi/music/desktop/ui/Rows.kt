package ca.ilianokokoro.umihi.music.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.core.domain.Track
import ca.ilianokokoro.umihi.music.core.domain.formatTrackDuration
import ca.ilianokokoro.umihi.music.ui.components.SquareArtwork
import androidx.compose.foundation.shape.CircleShape

/** Same 60 dp row thumbnail the phone draws. */
val ARTWORK_SIZE = 60.dp

@Composable
fun TrackRow(track: Track, playing: Boolean, onClick: () -> Unit) {
    ListRow(
        artworkUri = track.displayArtwork.orEmpty(),
        title = track.title,
        subtitle = track.artist.name,
        trailing = formatTrackDuration(track.durationMs),
        highlighted = playing,
        onClick = onClick,
    )
}

@Composable
fun AlbumRow(title: String, subtitle: String, artworkUri: String, onClick: () -> Unit) {
    ListRow(
        artworkUri = artworkUri,
        title = title,
        subtitle = subtitle,
        trailing = "",
        highlighted = false,
        onClick = onClick,
    )
}

@Composable
fun ArtistRow(name: String, artworkUri: String, onClick: () -> Unit) {
    ListRow(
        artworkUri = artworkUri,
        title = name,
        subtitle = "",
        trailing = "",
        highlighted = false,
        circularArtwork = true,
        onClick = onClick,
    )
}

@Composable
private fun ListRow(
    artworkUri: String,
    title: String,
    subtitle: String,
    trailing: String,
    highlighted: Boolean,
    onClick: () -> Unit,
    circularArtwork: Boolean = false,
) {
    val background = if (highlighted) {
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
            uri = artworkUri,
            modifier = if (circularArtwork) {
                Modifier.size(ARTWORK_SIZE).clip(CircleShape)
            } else {
                Modifier.size(ARTWORK_SIZE)
            },
            cornerRadius = if (circularArtwork) ARTWORK_SIZE else 8.dp,
            requestSize = ARTWORK_SIZE,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing.isNotBlank()) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
