package ca.ilianokokoro.umihi.music.ui.screens.artist

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.models.ArtistAlbum
import ca.ilianokokoro.umihi.music.ui.components.BackButton
import ca.ilianokokoro.umihi.music.ui.components.ErrorMessage
import ca.ilianokokoro.umihi.music.ui.components.MusicSkeleton
import ca.ilianokokoro.umihi.music.ui.components.MusicSkeletonLayout
import ca.ilianokokoro.umihi.music.ui.components.SquareImage
import ca.ilianokokoro.umihi.music.ui.components.localizedApiSectionTitle

/** Complete album, EP or singles shelf opened from an artist page. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArtistReleasesScreen(
    title: String,
    browseId: String,
    params: String,
    onBack: () -> Unit,
    onOpenAlbum: (id: String, name: String, cover: String) -> Unit,
    application: Application,
    viewModel: ArtistReleasesViewModel = viewModel(
        factory = ArtistReleasesViewModel.Factory(application, browseId, params)
    ),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value

    Scaffold(contentWindowInsets = WindowInsets.systemBars) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackButton(onBack = onBack)
                Text(
                    text = localizedApiSectionTitle(title),
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            when (state) {
                ArtistReleasesState.Loading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    MusicSkeleton(MusicSkeletonLayout.Library)
                }

                is ArtistReleasesState.Error -> ErrorMessage(
                    ex = state.exception,
                    onRetry = viewModel::load,
                )

                is ArtistReleasesState.Success -> {
                    if (state.releases.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = androidx.compose.ui.res.stringResource(R.string.no_results),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            columns = GridCells.Adaptive(minSize = 150.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(
                                start = 12.dp,
                                end = 12.dp,
                                top = 8.dp,
                                bottom = Constants.Ui.SCROLLABLE_BOTTOM_PADDING,
                            ),
                        ) {
                            items(state.releases, key = ArtistAlbum::browseId) { release ->
                                ArtistReleaseGridCard(
                                    release = release,
                                    onClick = {
                                        onOpenAlbum(
                                            release.browseId,
                                            release.title,
                                            release.thumbnail,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistReleaseGridCard(
    release: ArtistAlbum,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SquareImage(
            uri = release.thumbnail,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = release.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (release.subtitle.isNotBlank()) {
            Text(
                text = release.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
