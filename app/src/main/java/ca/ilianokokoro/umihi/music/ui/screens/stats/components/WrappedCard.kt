package ca.ilianokokoro.umihi.music.ui.screens.stats.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import ca.ilianokokoro.umihi.music.ui.components.studio.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.ui.screens.stats.WrappedStats
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Highlighted year-in-review card summarizing the current year of listening. */
@Composable
fun WrappedCard(
    wrapped: WrappedStats,
    modifier: Modifier = Modifier,
) {
    val contentColor = MaterialTheme.colorScheme.onSurface

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.wrapped_title, wrapped.year),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor,
            )

            WrappedRow(
                label = stringResource(R.string.wrapped_total_plays),
                value = pluralStringResource(
                    R.plurals.play_count,
                    wrapped.totalPlays,
                    wrapped.totalPlays,
                ),
                contentColor = contentColor,
            )

            if (wrapped.listeningTimeMs > 0) {
                val totalMinutes = wrapped.listeningTimeMs / 60_000
                WrappedRow(
                    label = stringResource(R.string.wrapped_listening_time),
                    value = stringResource(
                        R.string.wrapped_hours_minutes,
                        totalMinutes / 60,
                        totalMinutes % 60,
                    ),
                    contentColor = contentColor,
                )
            }

            wrapped.topArtist?.let { artist ->
                WrappedRow(
                    label = stringResource(R.string.wrapped_top_artist),
                    value = artist.artist,
                    contentColor = contentColor,
                )
            }

            wrapped.topTrack?.let { track ->
                WrappedRow(
                    label = stringResource(R.string.wrapped_top_track),
                    value = track.title,
                    contentColor = contentColor,
                )
            }

            wrapped.mostActiveDay?.let { day ->
                WrappedRow(
                    label = stringResource(R.string.wrapped_most_active_day),
                    value = day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                    contentColor = contentColor,
                )
            }
        }
    }
}

@Composable
private fun WrappedRow(
    label: String,
    value: String,
    contentColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor.copy(alpha = 0.8f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            maxLines = 1,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}
