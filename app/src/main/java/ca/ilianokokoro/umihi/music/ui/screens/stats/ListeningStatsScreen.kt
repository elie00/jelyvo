package ca.ilianokokoro.umihi.music.ui.screens.stats

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.managers.PlayerManager
import ca.ilianokokoro.umihi.music.core.stats.StatsExporter
import ca.ilianokokoro.umihi.music.ui.components.BackButton
import ca.ilianokokoro.umihi.music.ui.components.SquareImage
import ca.ilianokokoro.umihi.music.ui.screens.stats.components.ListeningHeatmap
import ca.ilianokokoro.umihi.music.ui.screens.stats.components.WrappedCard
import ca.ilianokokoro.umihi.music.ui.components.frosted
import ca.ilianokokoro.umihi.music.ui.components.frostedTopAppBarColors
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.runtime.remember

@Composable
fun ListeningStatsScreen(
    application: Application,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    // Stats only record artist names, never ids: a search is the honest way to reach the page.
    onSearchArtist: (String) -> Unit = {},
    viewModel: ListeningStatsViewModel = viewModel(
        factory = ListeningStatsViewModel.Factory(application),
    ),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    val hasStats = state.topTracks.isNotEmpty() || state.topArtists.isNotEmpty()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportStats(it) }
    }

    val hazeState = remember { HazeState() }
    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                modifier = Modifier.frosted(hazeState),
                colors = frostedTopAppBarColors(),
                title = {
                    androidx.compose.foundation.layout.Column {
                        Text(stringResource(R.string.listening_stats))
                        Text(
                            text = stringResource(state.period.labelRes),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = { BackButton(onBack = onBack) },
                actions = {
                    if (hasStats) {
                        IconButton(
                            enabled = !state.exportInProgress,
                            onClick = {
                                exportLauncher.launch(StatsExporter.SUGGESTED_FILE_NAME)
                            },
                        ) {
                            // Exporting is silent otherwise: the icon stayed put and the button
                            // simply stopped responding.
                            if (state.exportInProgress) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.FileDownload,
                                    contentDescription = stringResource(
                                        R.string.stats_export_title
                                    ),
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().hazeSource(hazeState),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = paddingValues.calculateTopPadding(),
                bottom = Constants.Ui.SCROLLABLE_BOTTOM_PADDING,
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            state.wrapped?.let { wrapped ->
                item {
                    WrappedCard(
                        wrapped = wrapped,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                    )
                }
            }

            item {
                PeriodSelector(
                    selected = state.period,
                    onSelect = viewModel::selectPeriod,
                )
            }

            if (state.dailyCounts.isNotEmpty()) {
                item { StatsSectionTitle(stringResource(R.string.listening_activity)) }
                item {
                    ListeningHeatmap(
                        dailyCounts = state.dailyCounts,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }

            if (!hasStats) {
                item {
                    Text(
                        text = stringResource(R.string.no_listening_stats),
                        modifier = Modifier.padding(24.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (state.topTracks.isNotEmpty()) {
                item { StatsSectionTitle(stringResource(R.string.top_tracks)) }
                itemsIndexed(
                    items = state.topTracks,
                    key = { _, stat -> stat.youtubeId },
                ) { index, stat ->
                    ListItem(
                        headlineContent = {
                            Text(
                                text = "${index + 1}. ${stat.title}",
                                maxLines = 1,
                            )
                        },
                        supportingContent = { Text(stat.artist, maxLines = 1) },
                        leadingContent = {
                            SquareImage(
                                uri = stat.thumbnailHref,
                                modifier = Modifier.size(56.dp),
                                cornerRadius = 8.dp,
                            )
                        },
                        trailingContent = {
                            Text(
                                pluralStringResource(
                                    R.plurals.play_count,
                                    stat.playCount,
                                    stat.playCount,
                                ),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        },
                        modifier = Modifier
                            .clickable {
                                PlayerManager.playSong(stat.toSong())
                                onOpenPlayer()
                            },
                    )
                }
            }

            if (state.topArtists.isNotEmpty()) {
                item { StatsSectionTitle(stringResource(R.string.top_artists)) }
                itemsIndexed(
                    items = state.topArtists,
                    key = { _, stat -> stat.artist },
                ) { index, stat ->
                    ListItem(
                        modifier = Modifier.clickable { onSearchArtist(stat.artist) },
                        headlineContent = { Text(stat.artist, maxLines = 1) },
                        leadingContent = {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        },
                        trailingContent = {
                            Text(
                                pluralStringResource(
                                    R.plurals.play_count,
                                    stat.playCount,
                                    stat.playCount,
                                ),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PeriodSelector(
    selected: StatsPeriod,
    onSelect: (StatsPeriod) -> Unit,
) {
    val periods = StatsPeriod.entries

    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
    ) {
        periods.forEachIndexed { index, period ->
            SegmentedButton(
                selected = period == selected,
                onClick = { onSelect(period) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = periods.size,
                ),
                label = {
                    Text(
                        text = stringResource(period.labelRes),
                        maxLines = 1,
                    )
                },
            )
        }
    }
}

@Composable
private fun StatsSectionTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(start = 8.dp, top = 20.dp, bottom = 8.dp),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
    )
}
