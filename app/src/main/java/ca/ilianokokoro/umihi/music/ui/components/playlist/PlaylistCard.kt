package ca.ilianokokoro.umihi.music.ui.components.playlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.models.PlaylistInfo
import ca.ilianokokoro.umihi.music.ui.components.SquareImage

@Composable
fun PlaylistCard(
    playlistInfo: PlaylistInfo,
    onClicked: () -> Unit,
    modifier: Modifier = Modifier,
    /** Artwork of the first tracks; on-device playlists have no cover of their own. */
    coverUris: List<String> = emptyList(),
) {
    Card(
        onClick = onClicked,
        modifier = modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(12.dp)
                .fillMaxSize()
        ) {
            val specialCover = specialPlaylistCover(playlistInfo.id)
            when {
                // Les playlists automatiques portent la pochette de marque plutot que le visuel
                // generique servi par YouTube (ou aucun, pour les telechargements).
                specialCover != null -> JelyvoPlaylistCover(
                    cover = specialCover,
                    contentDescription = stringResource(R.string.playlist_cover),
                )

                coverUris.isNotEmpty() -> PlaylistMosaicCover(
                    uris = coverUris,
                    contentDescription = stringResource(R.string.playlist_cover),
                    modifier = Modifier.fillMaxWidth(),
                )

                else -> SquareImage(
                    uri = playlistInfo.coverPath ?: playlistInfo.coverHref,
                    contentDescription = stringResource(R.string.playlist_cover),
                )
            }

            Text(
                text = playlistInfo.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}
