package ca.ilianokokoro.umihi.music.ui.components.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.ui.components.SquareImage
import ca.ilianokokoro.umihi.music.ui.screens.home.HomeMix
import ca.ilianokokoro.umihi.music.ui.screens.home.HomeMixKind

/** Resolves the display title of a locally generated mix. */
@Composable
fun homeMixTitle(kind: HomeMixKind): String = stringResource(
    when (kind) {
        HomeMixKind.DAILY -> R.string.home_mix_daily
        HomeMixKind.CONTEXT_MORNING -> R.string.home_mix_morning
        HomeMixKind.CONTEXT_EVENING -> R.string.home_mix_evening
        HomeMixKind.REPLAY -> R.string.home_mix_replay
        HomeMixKind.REDISCOVER -> R.string.home_mix_rediscover
    }
)

/**
 * "For you" section: a couple of cards for the mixes derived from the local play history.
 * Tapping a card queues the whole mix.
 */
@Composable
fun ForYouMixesRow(
    mixes: List<HomeMix>,
    onMixClick: (HomeMix) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.home_for_you),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            items(items = mixes, key = { it.kind }) { mix ->
                MixCard(mix = mix, onClick = { onMixClick(mix) })
            }
        }
    }
}

@Composable
private fun MixCard(
    mix: HomeMix,
    onClick: () -> Unit,
) {
    val cover = mix.songs.first()
    Column(
        modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SquareImage(
            uri = cover.thumbnailPath ?: cover.thumbnailHref,
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 18.dp,
        )
        Text(
            text = homeMixTitle(mix.kind),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = pluralStringResource(R.plurals.song_count, mix.songs.size, mix.songs.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
