@file:OptIn(ExperimentalMaterial3Api::class)

package ca.ilianokokoro.umihi.music.ui.screens.musicimport

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.models.importing.ExternalMusicPlaylist
import ca.ilianokokoro.umihi.music.models.importing.MusicImportProgress
import ca.ilianokokoro.umihi.music.models.importing.MusicImportService
import ca.ilianokokoro.umihi.music.ui.components.BackButton

@Composable
fun MusicImportScreen(
    application: Application,
    onBack: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    musicImportViewModel: MusicImportViewModel = viewModel(
        factory = MusicImportViewModel.Factory(application)
    ),
) {
    val uiState = musicImportViewModel.uiState.collectAsStateWithLifecycle().value

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.music_import_title)) },
                navigationIcon = { BackButton(onBack = onBack) },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding()),
        ) {
            when (val state = uiState.screenState) {
                MusicImportScreenState.Ready -> LinkContent(
                    link = uiState.link,
                    onLinkChange = musicImportViewModel::updateLink,
                    onAnalyze = musicImportViewModel::analyze,
                )

                MusicImportScreenState.Resolving -> LoadingContent(
                    title = stringResource(R.string.music_import_resolving),
                )

                MusicImportScreenState.Preview -> uiState.playlist?.let { playlist ->
                    PreviewContent(
                        playlist = playlist,
                        onImport = musicImportViewModel::startImport,
                        onEdit = musicImportViewModel::editLink,
                    )
                }

                is MusicImportScreenState.Importing -> ImportingContent(state.progress)

                is MusicImportScreenState.Complete -> CompleteContent(
                    progress = state.progress,
                    onOpenPlaylist = onOpenPlaylist,
                    onImportAnother = musicImportViewModel::editLink,
                )

                is MusicImportScreenState.Error -> ErrorContent(
                    onRetry = musicImportViewModel::analyze,
                    onEdit = musicImportViewModel::editLink,
                )
            }
        }
    }
}

@Composable
private fun LinkContent(
    link: String,
    onLinkChange: (String) -> Unit,
    onAnalyze: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.music_import_heading),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.music_import_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            ServiceRow()
        }
        item {
            OutlinedTextField(
                value = link,
                onValueChange = onLinkChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.music_import_link_label)) },
                placeholder = { Text(stringResource(R.string.music_import_link_placeholder)) },
                leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                minLines = 2,
                maxLines = 4,
            )
        }
        item {
            Button(
                onClick = onAnalyze,
                enabled = link.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.music_import_analyze))
            }
        }
        item {
            Text(
                text = stringResource(R.string.music_import_public_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ServiceRow() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.music_import_supported_services),
            style = MaterialTheme.typography.labelLarge,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("Spotify", "Apple Music", "Deezer").forEach { service ->
                ElevatedCard(modifier = Modifier.weight(1f)) {
                    Text(
                        text = service,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 14.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewContent(
    playlist: ExternalMusicPlaylist,
    onImport: () -> Unit,
    onEdit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.LibraryMusic,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(
                R.string.music_import_preview,
                playlist.service.displayName(),
                playlist.tracks.size,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(28.dp))
        Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.music_import_start))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.music_import_edit_link))
        }
    }
}

@Composable
private fun LoadingContent(title: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ImportingContent(progress: MusicImportProgress) {
    val fraction = if (progress.total == 0) {
        0f
    } else {
        progress.processed.toFloat() / progress.total.toFloat()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.music_import_matching),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(20.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(
                R.string.music_import_progress,
                progress.processed,
                progress.total,
            ),
        )
        Text(
            text = stringResource(
                R.string.music_import_progress_result,
                progress.matchedCount,
                progress.failureCount,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CompleteContent(
    progress: MusicImportProgress,
    onOpenPlaylist: (String) -> Unit,
    onImportAnother: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.music_import_complete),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(
                R.string.music_import_complete_summary,
                progress.matchedCount,
                progress.total,
            ),
            textAlign = TextAlign.Center,
        )
        if (progress.failureCount > 0) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.music_import_failed_summary,
                    progress.failureCount,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(28.dp))
        progress.createdPlaylistId?.let { playlistId ->
            Button(
                onClick = { onOpenPlaylist(playlistId) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.music_import_view_playlist))
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        OutlinedButton(
            onClick = onImportAnother,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.music_import_another))
        }
    }
}

@Composable
private fun ErrorContent(
    onRetry: () -> Unit,
    onEdit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.music_import_error),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.music_import_error_detail),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        FilledTonalButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.retry))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.music_import_edit_link))
        }
    }
}

private fun MusicImportService.displayName(): String = when (this) {
    MusicImportService.SPOTIFY -> "Spotify"
    MusicImportService.APPLE_MUSIC -> "Apple Music"
    MusicImportService.DEEZER -> "Deezer"
}
