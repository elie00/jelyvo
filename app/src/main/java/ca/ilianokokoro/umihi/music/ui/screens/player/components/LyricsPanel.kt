/*
 * Line falloff, sweep masking and scroll behaviour adapted from BitChord's PlayerLyrics
 * (https://github.com/kushagrasinghx/BitChord), GPL-3.0.
 * Copyright (C) Kushagra Singh and BitChord contributors.
 * Reduced to word timing: no syllables, duets, background vocals or translation.
 */
package ca.ilianokokoro.umihi.music.ui.screens.player.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.ilianokokoro.umihi.music.R
import kotlinx.coroutines.launch
import ca.ilianokokoro.umihi.music.ui.components.studio.CircularProgressIndicator
import ca.ilianokokoro.umihi.music.ui.components.studio.CircleButton
import ca.ilianokokoro.umihi.music.core.lyrics.LyricsTranslation
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.Icons
import android.widget.Toast
import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.models.LyricLine
import ca.ilianokokoro.umihi.music.models.Lyrics
import ca.ilianokokoro.umihi.music.ui.components.LoadingAnimation
import ca.ilianokokoro.umihi.music.ui.components.studio.TextButton
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** Opacity by distance from the line being sung: the eye goes to the words, not the wall. */
private val LINE_FALLOFF_ALPHA = floatArrayOf(1f, 0.74f, 0.6f, 0.48f, 0.4f, 0.34f)

/** What has not been sung yet in the active line. Readable ahead of time, plainly not lit. */
private const val UNSUNG_ALPHA = 0.45f

/** While the listener reads through the lyrics on their own, no line is the focus. */
private const val BROWSING_ALPHA = 0.8f
private const val BROWSING_HOLD_MS = 3_000L
private const val INACTIVE_SCALE = 0.98f
private const val ACTIVE_LINE_ANCHOR = 0.22f
private val WIPE_FEATHER = 30.dp
private val EDGE_FADE = 24.dp

internal fun lineAlpha(distanceFromActive: Int): Float =
    LINE_FALLOFF_ALPHA[abs(distanceFromActive).coerceAtMost(LINE_FALLOFF_ALPHA.lastIndex)]

/**
 * How far into [line]'s text the singing has reached at [positionMs], in characters. Inside a word
 * it moves through the word at the word's own pace, and between two words it rests at the end of
 * the first rather than creeping into a word nobody has started yet.
 */
internal fun sungCharPosition(line: LyricLine, positionMs: Long): Float {
    var sung = 0f
    var searchFrom = 0
    for (word in line.words) {
        val start = line.text.indexOf(word.text, searchFrom)
        if (start < 0) continue
        val end = start + word.text.length
        if (positionMs < word.startMs) return sung
        if (positionMs < word.endMs) {
            val span = (word.endMs - word.startMs).coerceAtLeast(1L)
            return start + (positionMs - word.startMs).toFloat() / span * word.text.length
        }
        sung = end.toFloat()
        searchFrom = end
    }
    return line.text.length.toFloat()
}

/**
 * The player reports its position every 150 ms or so, far too coarsely for a sweep, so the panel
 * runs its own clock between reports. It takes a report that is ahead, or far enough off to be a
 * seek, and otherwise keeps going: small backward corrections would make the sweep stutter.
 */
internal fun acceptPositionReport(displayedMs: Long, reportedMs: Long): Boolean =
    reportedMs >= displayedMs || displayedMs - reportedMs > 400L

/**
 * Displays the lyrics for the current song.
 *
 * - [ApiResult.Loading]: shows a loading animation.
 * - [ApiResult.Error]: shows an "unavailable" message (also covers the "no lyrics found" case,
 *   which the repository surfaces as an error).
 * - [ApiResult.Success]: renders the lines in a [LazyColumn]. When the lyrics are synced, the line
 *   matching [currentPositionMs] is in focus and the list keeps it in view.
 *
 * @param currentPositionMs current playback position in milliseconds.
 * @param isPlaying runs the sweep's own clock between position reports.
 * @param playbackSpeed the pace of that clock.
 */
@Composable
fun LyricsPanel(
    lyricsState: ApiResult<Lyrics>,
    currentPositionMs: Long,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    playbackSpeed: Float = 1f,
    onSeekTo: (Long) -> Unit = {},
    onRetry: () -> Unit = {},
    onShareLine: (String) -> Unit = {},
    translate: suspend (Lyrics, String) -> LyricsTranslation.Result = LyricsTranslation::translate,
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
                    val translation = rememberLyricsTranslation(lyrics, translate)
                    SyncedLyricsList(
                        lyrics = translation.shown,
                        currentPositionMs = currentPositionMs,
                        isPlaying = isPlaying,
                        playbackSpeed = playbackSpeed,
                        onSeekTo = onSeekTo,
                        onShareLine = onShareLine,
                    )
                    LyricsTranslationButton(
                        translation = translation,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                    )
                }
            }
        }
    }
}

private class LyricsTranslationState(
    val shown: Lyrics,
    val translated: Boolean,
    val loading: Boolean,
    val toggle: () -> Unit,
)

/**
 * What the panel shows and the toggle that switches it. The original is never replaced, so going
 * back is instant; so is coming back again, which never asks twice for the same song.
 */
@Composable
private fun rememberLyricsTranslation(
    original: Lyrics,
    translate: suspend (Lyrics, String) -> LyricsTranslation.Result,
): LyricsTranslationState {
    val context = LocalContext.current
    val language = LocalConfiguration.current.locales[0].toLanguageTag()
    val scope = rememberCoroutineScope()
    var translation by remember(original, language) { mutableStateOf<Lyrics?>(null) }
    var showing by remember(original, language) { mutableStateOf(false) }
    var loading by remember(original, language) { mutableStateOf(false) }
    // A song that changes while its request is out must not receive the answer.
    val current by rememberUpdatedState(original)

    return LyricsTranslationState(
        shown = translation?.takeIf { showing } ?: original,
        translated = showing,
        loading = loading,
        toggle = toggle@{
            if (loading) return@toggle
            if (showing || translation != null) {
                showing = !showing
                return@toggle
            }
            val asked = original
            loading = true
            scope.launch {
                val result = translate(asked, language)
                if (asked !== current) return@launch
                loading = false
                when (result) {
                    is LyricsTranslation.Result.Translated -> {
                        translation = result.lyrics
                        showing = true
                    }

                    is LyricsTranslation.Result.SameLanguage -> Toast.makeText(
                        context,
                        R.string.lyrics_already_in_language,
                        Toast.LENGTH_SHORT,
                    ).show()

                    LyricsTranslation.Result.Unavailable -> Toast.makeText(
                        context,
                        R.string.lyrics_translation_failed,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        },
    )
}

@Composable
private fun LyricsTranslationButton(
    translation: LyricsTranslationState,
    modifier: Modifier = Modifier,
) {
    CircleButton(
        onClick = translation.toggle,
        modifier = modifier,
        size = 44.dp,
        filled = translation.translated,
    ) {
        if (translation.loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = LocalContentColor.current,
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Translate,
                contentDescription = stringResource(
                    if (translation.translated) R.string.lyrics_show_original else R.string.lyrics_translate
                ),
                modifier = Modifier.size(20.dp),
            )
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

/** Smooth playback time for the sweep. Read it while drawing, so only the drawing is redone. */
@Composable
private fun rememberLyricPlayhead(
    reportedMs: Long,
    running: Boolean,
    speed: Float,
): () -> Long {
    val displayed = remember { mutableLongStateOf(reportedMs) }
    LaunchedEffect(reportedMs) {
        if (acceptPositionReport(displayed.longValue, reportedMs)) {
            displayed.longValue = reportedMs
        }
    }
    LaunchedEffect(running, speed) {
        if (!running) return@LaunchedEffect
        var lastFrame = withFrameMillis { it }
        while (true) {
            withFrameMillis { now ->
                displayed.longValue += ((now - lastFrame) * speed).toLong()
                lastFrame = now
            }
        }
    }
    return { displayed.longValue }
}

@Composable
private fun SyncedLyricsList(
    lyrics: Lyrics,
    currentPositionMs: Long,
    isPlaying: Boolean,
    playbackSpeed: Float,
    onSeekTo: (Long) -> Unit,
    onShareLine: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    var browsing by remember { mutableStateOf(false) }

    // The last line whose timestamp has already passed, for synced lyrics only.
    val activeIndex = remember(lyrics, currentPositionMs) {
        if (!lyrics.isSynced) {
            -1
        } else {
            lyrics.lines.indexOfLast { line ->
                line.timeMs != null && line.timeMs <= currentPositionMs
            }
        }
    }
    val playhead = rememberLyricPlayhead(
        reportedMs = currentPositionMs,
        // The clock only serves the sweep: lyrics without word timing never need its frames.
        running = isPlaying && lyrics.hasWordTiming,
        speed = playbackSpeed,
    )

    // A listener reading ahead keeps their place until they have let go for a moment.
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collectLatest { interaction ->
            when (interaction) {
                is DragInteraction.Start -> browsing = true
                is DragInteraction.Stop, is DragInteraction.Cancel -> {
                    delay(BROWSING_HOLD_MS)
                    browsing = false
                }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val anchorPx = with(density) { (maxHeight * ACTIVE_LINE_ANCHOR).roundToPx() }

        LaunchedEffect(activeIndex, browsing) {
            if (activeIndex >= 0 && !browsing) {
                listState.animateScrollToItem(activeIndex, -anchorPx)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    // Lines dissolve into the edges instead of being cut off by them.
                    val fade = EDGE_FADE.toPx()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            fade / size.height to Color.Black,
                            1f - fade / size.height to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
            contentPadding = PaddingValues(
                start = 24.dp,
                end = 24.dp,
                top = EDGE_FADE,
                // Lets the last line reach the same place as all the others.
                bottom = maxHeight * (1f - ACTIVE_LINE_ANCHOR),
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(lyrics.lines) { index, line ->
                val alpha = when {
                    !lyrics.isSynced -> 1f
                    browsing -> BROWSING_ALPHA
                    activeIndex < 0 -> lineAlpha(index + 1)
                    else -> lineAlpha(index - activeIndex)
                }
                LyricRow(
                    line = line,
                    isActive = index == activeIndex,
                    targetAlpha = alpha,
                    playhead = playhead,
                    onSeekTo = onSeekTo,
                    onShare = { onShareLine(line.text) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LyricRow(
    line: LyricLine,
    isActive: Boolean,
    targetAlpha: Float,
    playhead: () -> Long,
    onSeekTo: (Long) -> Unit,
    onShare: () -> Unit,
) {
    val alpha by animateFloatAsState(targetAlpha, tween(350), label = "lyricLineAlpha")
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1f else INACTIVE_SCALE,
        animationSpec = tween(350),
        label = "lyricLineScale",
    )
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val shareLabel = stringResource(R.string.share_lyrics)
    val isSpoken = line.text.isNotBlank()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = line.timeMs != null || isSpoken,
                onLongClickLabel = shareLabel,
                onLongClick = if (isSpoken) {
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onShare()
                    }
                } else {
                    null
                },
                onClick = { line.timeMs?.let(onSeekTo) },
            ),
    ) {
        if (isSpoken) {
            LyricText(line = line, sweeping = isActive && line.words.isNotEmpty(), playhead = playhead)
        } else {
            InstrumentalBreak()
        }
    }
}

@Composable
private fun LyricText(line: LyricLine, sweeping: Boolean, playhead: () -> Long) {
    // Not state: the layout is only read while drawing, and changing it must not recompose.
    val layout = remember { arrayOfNulls<TextLayoutResult>(1) }
    Text(
        text = line.text,
        onTextLayout = { layout[0] = it },
        style = MaterialTheme.typography.headlineLarge.copy(
            fontWeight = FontWeight.W800,
            fontSize = 28.sp,
            lineHeight = 35.sp,
            letterSpacing = (-0.3).sp,
        ),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = if (sweeping) {
            Modifier
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    layout[0]?.let { result ->
                        maskUnsung(
                            layout = result,
                            sungChars = sungCharPosition(line, playhead()),
                            feather = WIPE_FEATHER.toPx(),
                        )
                    }
                }
        } else {
            Modifier
        },
    )
}

/**
 * Dims everything past [sungChars], visual line by visual line. Keeps what is drawn underneath in
 * proportion to the mask's alpha, so a soft edge costs one gradient. The gradient keeps its full
 * width even where the text stops, so the last letters of a line fade up rather than snap.
 */
private fun ContentDrawScope.maskUnsung(
    layout: TextLayoutResult,
    sungChars: Float,
    feather: Float,
) {
    val unsung = Color.Black.copy(alpha = UNSUNG_ALPHA)
    for (visualLine in 0 until layout.lineCount) {
        val start = layout.getLineStart(visualLine)
        val end = layout.getLineEnd(visualLine)
        if (sungChars >= end) continue

        val top = layout.getLineTop(visualLine)
        val band = Size(size.width, layout.getLineBottom(visualLine) - top)
        if (sungChars <= start) {
            drawRect(unsung, Offset(0f, top), band, blendMode = BlendMode.DstIn)
            continue
        }

        val char = sungChars.toInt().coerceAtMost(layout.layoutInput.text.length - 1)
        val from = layout.getHorizontalPosition(char, usePrimaryDirection = true)
        val to = layout.getHorizontalPosition(char + 1, usePrimaryDirection = true)
        val edge = from + (to - from) * (sungChars - char)
        val rtl = layout.getParagraphDirection(char) == ResolvedTextDirection.Rtl
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Black,
                1f to unsung,
                startX = edge,
                endX = if (rtl) edge - feather else edge + feather,
            ),
            topLeft = Offset(0f, top),
            size = band,
            blendMode = BlendMode.DstIn,
        )
    }
}

/** A blank timed line is a break in the singing: three dots rather than a hole in the list. */
@Composable
private fun InstrumentalBreak() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier.padding(vertical = 10.dp),
    ) {
        repeat(3) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface)
            )
        }
    }
}
