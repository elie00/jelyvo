package ca.ilianokokoro.umihi.music.ui.screens.recodebug

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.reco.DayBucket
import ca.ilianokokoro.umihi.music.core.reco.RecoWeights
import ca.ilianokokoro.umihi.music.core.reco.SourceContextHealth
import ca.ilianokokoro.umihi.music.ui.components.BackButton
import ca.ilianokokoro.umihi.music.ui.components.frostedTopAppBarColors
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecoDebugScreen(
    application: Application,
    onBack: () -> Unit,
    viewModel: RecoDebugViewModel = viewModel(
        factory = RecoDebugViewModel.Factory(application),
    ),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            CenterAlignedTopAppBar(
                colors = frostedTopAppBarColors(),
                title = {
                    Text(
                        stringResource(R.string.reco_debug_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = { BackButton(onBack = onBack) },
                actions = {
                    IconButton(
                        onClick = viewModel::recomputeProfile,
                        enabled = !state.refreshing,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.reco_debug_recompute),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        if (state.loading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            RecoDebugContent(
                state = state,
                onRecompute = viewModel::recomputeProfile,
                onResetWeights = viewModel::resetWeights,
                modifier = Modifier.padding(paddingValues),
            )
        }
    }
}

@Composable
private fun RecoDebugContent(
    state: RecoDebugState,
    onRecompute: () -> Unit,
    onResetWeights: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = Constants.Ui.SCROLLABLE_BOTTOM_PADDING,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SummaryCard(state)
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onRecompute,
                    enabled = !state.refreshing,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.reco_debug_recompute))
                }
                OutlinedButton(
                    onClick = onResetWeights,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.reco_debug_reset_weights))
                }
            }
        }

        state.weights?.let { stored ->
            item { SectionTitle(stringResource(R.string.reco_debug_weights)) }
            item {
                WeightCard(
                    title = stringResource(R.string.reco_debug_daily_weights),
                    weights = stored.daily,
                )
            }
            item {
                WeightCard(
                    title = stringResource(R.string.reco_debug_context_weights),
                    weights = stored.context,
                )
            }
        }

        if (state.health7d.isNotEmpty() || state.health30d.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.reco_debug_health)) }
            if (state.health7d.isNotEmpty()) {
                item {
                    HealthCard(
                        title = stringResource(R.string.reco_debug_last_7_days),
                        rows = state.health7d,
                    )
                }
            }
            if (state.health30d.isNotEmpty()) {
                item {
                    HealthCard(
                        title = stringResource(R.string.reco_debug_last_30_days),
                        rows = state.health30d,
                    )
                }
            }
        }

        if (state.hourlyTopArtists.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.reco_debug_time_profiles)) }
            item { HourlyProfileCard(state.hourlyTopArtists) }
        }

        if (state.topArtists.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.reco_debug_top_artists)) }
            items(state.topArtists, key = { it.label }) { row ->
                AffinityCard(row)
            }
        }

        if (state.topTracks.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.reco_debug_top_tracks)) }
            items(state.topTracks, key = { "${it.label}:${it.sublabel}" }) { row ->
                AffinityCard(row)
            }
        }
    }
}

@Composable
private fun SummaryCard(state: RecoDebugState) {
    val computedAt = if (state.profileComputedAtMs > 0L) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(state.profileComputedAtMs))
    } else {
        stringResource(R.string.reco_debug_never)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.reco_debug_profile_summary),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.reco_debug_event_count, state.eventCount),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.reco_debug_computed_at, computedAt),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WeightCard(title: String, weights: RecoWeights) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            WeightRow(stringResource(R.string.reco_weight_song), weights.songAffinity)
            WeightRow(stringResource(R.string.reco_weight_artist), weights.artistAffinity)
            WeightRow(stringResource(R.string.reco_weight_colisten), weights.coListen)
            WeightRow(stringResource(R.string.reco_weight_context), weights.contextMatch)
            WeightRow(stringResource(R.string.reco_weight_novelty), weights.novelty)
            WeightRow(stringResource(R.string.reco_weight_fatigue), weights.fatigue)
            WeightRow(stringResource(R.string.reco_weight_skip), weights.skipPenalty)
        }
    }
}

@Composable
private fun WeightRow(label: String, value: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatScore(value), fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun HealthCard(title: String, rows: List<SourceContextHealth>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            rows.forEachIndexed { index, row ->
                if (index > 0) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(row.sourceContext, maxLines = 1)
                        Text(
                            text = stringResource(
                                R.string.reco_debug_plays_and_skips,
                                row.plays,
                                row.earlySkips,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = stringResource(
                            R.string.reco_debug_skip_rate,
                            row.skipRate * 100.0,
                        ),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun HourlyProfileCard(rows: Map<DayBucket, List<String>>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DayBucket.entries.forEach { bucket ->
                val artists = rows[bucket].orEmpty()
                if (artists.isNotEmpty()) {
                    Text(
                        text = dayBucketLabel(bucket),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = artists.joinToString(separator = " • "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AffinityCard(row: AffinityRow) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(row.label, maxLines = 1)
                if (row.sublabel.isNotBlank()) {
                    Text(
                        text = row.sublabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            Text(formatScore(row.score), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun dayBucketLabel(bucket: DayBucket): String = stringResource(
    when (bucket) {
        DayBucket.MORNING -> R.string.reco_debug_morning
        DayBucket.DAY -> R.string.reco_debug_day
        DayBucket.EVENING -> R.string.reco_debug_evening
        DayBucket.NIGHT -> R.string.reco_debug_night
    }
)

private fun formatScore(value: Double): String =
    String.format(Locale.getDefault(), "%.2f", value)
