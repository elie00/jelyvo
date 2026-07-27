package ca.ilianokokoro.umihi.music.ui.screens.player.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.models.Lyrics
import ca.ilianokokoro.umihi.music.models.LyricLine
import ca.ilianokokoro.umihi.music.ui.components.LoadingAnimation

/**
 * Displays the lyrics for the current song.
 *
 * - [ApiResult.Loading]: shows a loading animation.
 * - [ApiResult.Error]: shows an "unavailable" message (also covers the
 *   "no lyrics found" case, which the repository surfaces as an error).
 * - [ApiResult.Success]: renders the lines in a [LazyColumn]. When the lyrics
 *   are synced, the line matching [currentPositionMs] is highlighted and the
 *   list auto-scrolls to keep it centered.
 *
 * @param currentPositionMs current playback position in milliseconds.
 */
@Composable
fun LyricsPanel(
    lyricsState: ApiResult<Lyrics>,
    currentPositionMs: Long,
    modifier: Modifier = Modifier,
    onSeekTo: (Long) -> Unit = {},
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (lyricsState) {
            is ApiResult.Loading -> LoadingAnimation()

            is ApiResult.Error -> {
                Text(
                    text = stringResource(R.string.lyrics_unavailable),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp)
                )
            }

            is ApiResult.Success -> {
                val lyrics = lyricsState.data
                if (lyrics.isEmpty) {
                    Text(
                        text = stringResource(R.string.lyrics_unavailable),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp)
                    )
                } else {
                    SyncedLyricsList(
                        lyrics = lyrics,
                        currentPositionMs = currentPositionMs,
                        onSeekTo = onSeekTo,
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncedLyricsList(
    lyrics: Lyrics,
    currentPositionMs: Long,
    onSeekTo: (Long) -> Unit,
) {
    val listState = rememberLazyListState()

    // Index of the line currently being sung (only meaningful for synced lyrics):
    // the last line whose timestamp has already passed.
    val activeIndex = remember(lyrics, currentPositionMs) {
        if (!lyrics.isSynced) {
            -1
        } else {
            lyrics.lines.indexOfLast { line ->
                line.timeMs != null && line.timeMs <= currentPositionMs
            }
        }
    }

    // Auto-scroll to keep the active line visible.
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            listState.animateScrollToItem(activeIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        itemsIndexed(lyrics.lines) { index, line ->
            LyricRow(
                line = line,
                isActive = index == activeIndex,
                onSeekTo = onSeekTo,
            )
        }
    }
}

@Composable
private fun LyricRow(
    line: LyricLine,
    isActive: Boolean,
    onSeekTo: (Long) -> Unit,
) {
    val color by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
        },
        label = "lyricLineColor"
    )

    Text(
        text = line.text,
        style = if (isActive) {
            MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
        } else {
            MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal)
        },
        color = color,
        textAlign = TextAlign.Start,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (line.timeMs != null) {
                    Modifier.clickable { onSeekTo(line.timeMs) }
                } else Modifier
            )
    )
}
