@file:OptIn(ExperimentalMaterial3Api::class)

package ca.ilianokokoro.umihi.music.ui.screens.spotifyimport

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.data.repositories.ImportProgress
import ca.ilianokokoro.umihi.music.models.spotify.SpotifyPlaylist
import ca.ilianokokoro.umihi.music.ui.components.BackButton
import ca.ilianokokoro.umihi.music.ui.components.ErrorMessage
import ca.ilianokokoro.umihi.music.ui.components.LoadingAnimation

/**
 * Spotify import screen.
 *
 * @param onBack       pop navigation.
 * @param onStartAuth  invoked by the "Connect Spotify" button. The orchestrator wires this to
 *                     open the OAuth flow (Custom Tab / WebView) and, on success, must call
 *                     [SpotifyImportViewModel.onAuthCompleted] with the resulting access token.
 *                     (See the integration guide — the ViewModel is hoisted here for that reason.)
 */
@Composable
fun SpotifyImportScreen(
    application: Application,
    onBack: () -> Unit,
    onStartAuth: () -> Unit,
    spotifyImportViewModel: SpotifyImportViewModel = viewModel(
        factory = SpotifyImportViewModel.Factory(application)
    ),
) {
    val uiState = spotifyImportViewModel.uiState.collectAsStateWithLifecycle().value

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_from_spotify_title)) },
                navigationIcon = { BackButton(onBack = onBack) },
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding()),
            contentAlignment = Alignment.Center,
        ) {
            when (val state = uiState.screenState) {
                ScreenState.Disconnected -> ConnectContent(onStartAuth = onStartAuth)

                ScreenState.LoadingPlaylists -> LoadingAnimation()

                is ScreenState.PlaylistsLoaded -> PlaylistsContent(
                    playlists = state.playlists,
                    onImportLiked = spotifyImportViewModel::importLikedSongs,
                    onImportPlaylist = spotifyImportViewModel::importPlaylist,
                )

                is ScreenState.Importing -> ImportingContent(progress = state.progress)

                is ScreenState.ImportComplete -> ImportCompleteContent(
                    progress = state.progress,
                    onDone = spotifyImportViewModel::backToPlaylists,
                )

                is ScreenState.Error -> ErrorMessage(
                    ex = state.exception,
                    onRetry = spotifyImportViewModel::loadPlaylists,
                )
            }
        }
    }
}

@Composable
private fun ConnectContent(onStartAuth: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.spotify_connect_description),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onStartAuth) {
            Text(stringResource(R.string.spotify_connect_button))
        }
    }
}

@Composable
private fun PlaylistsContent(
    playlists: List<SpotifyPlaylist>,
    onImportLiked: () -> Unit,
    onImportPlaylist: (SpotifyPlaylist) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.spotify_choose_what_to_import),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        item {
            SourceCard(
                title = stringResource(R.string.liked_songs),
                subtitle = stringResource(R.string.spotify_liked_songs_subtitle),
                icon = Icons.Outlined.Favorite,
                onImport = onImportLiked,
            )
        }

        items(items = playlists, key = { it.id }) { playlist ->
            SourceCard(
                title = playlist.name,
                subtitle = stringResource(R.string.spotify_playlist_tracks, playlist.trackCount),
                icon = Icons.Outlined.LibraryMusic,
                onImport = { onImportPlaylist(playlist) },
            )
        }
    }
}

@Composable
private fun SourceCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onImport: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = onImport) {
                Text(stringResource(R.string.import_playlist))
            }
        }
    }
}

@Composable
private fun ImportingContent(progress: ImportProgress) {
    val fraction = if (progress.total > 0) {
        progress.processed.toFloat() / progress.total.toFloat()
    } else {
        0f
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.spotify_matching_tracks),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.spotify_progress_processed, progress.processed, progress.total),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(
                R.string.spotify_progress_matched_failed,
                progress.matchedCount,
                progress.failureCount
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ImportCompleteContent(
    progress: ImportProgress,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.spotify_import_complete),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(
                R.string.spotify_import_matched_summary,
                progress.matchedCount,
                progress.total
            ),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (progress.failureCount > 0) {
            Text(
                text = stringResource(R.string.spotify_import_failed_summary, progress.failureCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onDone) {
            Text(stringResource(R.string.done))
        }
    }
}
