package ca.ilianokokoro.umihi.music.ui.screens.player.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.models.Lyrics
import ca.ilianokokoro.umihi.music.models.LyricLine
import ca.ilianokokoro.umihi.music.ui.components.LoadingAnimation
import kotlin.math.ceil

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
    onRetry: () -> Unit = {},
    onShareLine: (String) -> Unit = {},
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (lyricsState) {
            is ApiResult.Loading -> LoadingAnimation()

            is ApiResult.Error -> {
                LyricsUnavailable(onRetry = onRetry)
            }

            is ApiResult.Success -> {
                val lyrics = lyricsState.data
                if (lyrics.isEmpty) {
                    LyricsUnavailable(onRetry = onRetry)
                } else {
                    SyncedLyricsList(
                        lyrics = lyrics,
                        currentPositionMs = currentPositionMs,
                        onSeekTo = onSeekTo,
                        onShareLine = onShareLine,
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricsUnavailable(onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(24.dp),
    ) {
        Text(
            text = stringResource(R.string.lyrics_unavailable),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.retry))
        }
    }
}

@Composable
private fun SyncedLyricsList(
    lyrics: Lyrics,
    currentPositionMs: Long,
    onSeekTo: (Long) -> Unit,
    onShareLine: (String) -> Unit,
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
    val activeProgress = remember(lyrics, activeIndex, currentPositionMs) {
        if (activeIndex < 0) {
            0f
        } else {
            val start = lyrics.lines[activeIndex].timeMs ?: currentPositionMs
            val end = lyrics.lines
                .asSequence()
                .drop(activeIndex + 1)
                .mapNotNull(LyricLine::timeMs)
                .firstOrNull()
                ?: (start + DEFAULT_LINE_DURATION_MS)
            if (end <= start) {
                1f
            } else {
                ((currentPositionMs - start).toFloat() / (end - start).toFloat())
                    .coerceIn(0f, 1f)
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
                activeProgress = if (index == activeIndex) activeProgress else 0f,
                onSeekTo = onSeekTo,
                onShare = { onShareLine(line.text) },
            )
        }
    }
}

@Composable
private fun LyricRow(
    line: LyricLine,
    isActive: Boolean,
    activeProgress: Float,
    onSeekTo: (Long) -> Unit,
    onShare: () -> Unit,
) {
    val color by animateColorAsState(
        targetValue = if (isActive) {
            // The current line stays readable while only the words already sung receive primary.
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.52f)
        },
        label = "lyricLineColor"
    )
    val highlightedColor = MaterialTheme.colorScheme.primary
    val displayedText = remember(line.text, isActive, activeProgress, highlightedColor) {
        karaokeText(
            text = line.text,
            progress = activeProgress,
            highlightedColor = highlightedColor,
            enabled = isActive,
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = displayedText,
            style = if (isActive) {
                MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
            } else {
                MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal)
            },
            color = color,
            textAlign = TextAlign.Start,
            modifier = Modifier
                .weight(1f)
                .then(
                    if (line.timeMs != null) {
                        Modifier.clickable { onSeekTo(line.timeMs) }
                    } else Modifier
                )
        )
        if (isActive && line.text.isNotBlank()) {
            IconButton(onClick = onShare, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = stringResource(R.string.share_lyrics),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * LRCLIB timestamps each line rather than every syllable. Interpolating between two line starts
 * gives a stable word-by-word progression while preserving the exact timing and seek behaviour.
 */
internal fun karaokeText(
    text: String,
    progress: Float,
    highlightedColor: androidx.compose.ui.graphics.Color,
    enabled: Boolean,
): AnnotatedString {
    if (!enabled || text.isBlank()) {
        return AnnotatedString(text)
    }

    val words = Regex("""\S+""").findAll(text).toList()
    if (words.isEmpty()) {
        return AnnotatedString(text)
    }
    val highlightedCount = ceil(progress.coerceIn(0f, 1f) * words.size)
        .toInt()
        .coerceIn(1, words.size)

    return buildAnnotatedString {
        append(text)
        words.take(highlightedCount).forEach { word ->
            addStyle(
                style = SpanStyle(
                    color = highlightedColor,
                    fontWeight = FontWeight.ExtraBold,
                ),
                start = word.range.first,
                end = word.range.last + 1,
            )
        }
    }
}

private const val DEFAULT_LINE_DURATION_MS = 5_000L
