/*
 * The idea of a year told in full-screen pages, with a poster to share, from BitChord's Replay
 * (https://github.com/kushagrasinghx/BitChord), GPL-3.0.
 * Copyright (C) Kushagra Singh and BitChord contributors.
 */
package ca.ilianokokoro.umihi.music.ui.screens.stats.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.createBitmap
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.helpers.ShareHelper
import ca.ilianokokoro.umihi.music.ui.components.SquareImage
import ca.ilianokokoro.umihi.music.ui.components.studio.ActionPill
import ca.ilianokokoro.umihi.music.ui.components.studio.CircleIconButton
import ca.ilianokokoro.umihi.music.ui.screens.stats.WrappedStats
import ca.ilianokokoro.umihi.music.ui.theme.ArtworkPageTheme
import ca.ilianokokoro.umihi.music.ui.theme.rememberArtworkPalette
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal sealed interface ReplayPage {
    data class Intro(val year: Int) : ReplayPage
    data class Totals(val plays: Int, val listeningTimeMs: Long) : ReplayPage
    data class TopArtist(val name: String, val plays: Int) : ReplayPage
    data class TopTrack(val title: String, val artist: String, val artwork: String, val plays: Int) : ReplayPage
    data class ActiveDay(val day: LocalDate, val plays: Int) : ReplayPage
}

/** Only what there is to say: a page with nothing on it would read as a broken one. */
internal fun replayPages(wrapped: WrappedStats): List<ReplayPage> = buildList {
    add(ReplayPage.Intro(wrapped.year))
    add(ReplayPage.Totals(wrapped.totalPlays, wrapped.listeningTimeMs))
    wrapped.topArtist?.let { add(ReplayPage.TopArtist(it.artist, it.playCount)) }
    wrapped.topTrack?.let { add(ReplayPage.TopTrack(it.title, it.artist, it.thumbnailHref, it.playCount)) }
    wrapped.mostActiveDay?.let { add(ReplayPage.ActiveDay(it, wrapped.mostActiveDayPlays)) }
}

@Composable
fun ReplayDialog(wrapped: WrappedStats, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val title = stringResource(R.string.wrapped_title, wrapped.year)
    val rows = replayPosterRows(wrapped)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        ReplayContent(
            wrapped = wrapped,
            onClose = onDismiss,
            onShare = {
                scope.launch { ShareHelper.shareReplay(context, title, rows) }
            },
        )
    }
}

/** The pages without the dialog's window, which a test cannot capture. */
@Composable
internal fun ReplayContent(
    wrapped: WrappedStats,
    onClose: () -> Unit,
    onShare: () -> Unit,
) {
    val pages = replayPages(wrapped)
    val pager = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    ArtworkPageTheme(rememberArtworkPalette(wrapped.topTrack?.thumbnailHref)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding()
                .padding(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                pages.indices.forEach { index ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.onBackground.copy(
                                    alpha = if (index <= pager.currentPage) 1f else 0.25f
                                )
                            ),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                CircleIconButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.close),
                    onClick = onClose,
                    size = 44.dp,
                )
            }
            HorizontalPager(
                state = pager,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // The left third goes back and the rest goes on, the way stories read anywhere.
                    .pointerInput(pages.size) {
                        detectTapGestures { offset ->
                            val forward = offset.x > size.width / 3f
                            val target = (pager.currentPage + if (forward) 1 else -1)
                                .coerceIn(0, pages.lastIndex)
                            scope.launch { pager.animateScrollToPage(target) }
                        }
                    },
            ) { index ->
                ReplayPageBody(pages[index])
            }
            ActionPill(
                icon = Icons.Rounded.Share,
                label = stringResource(R.string.share),
                onClick = onShare,
                filled = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ReplayPageBody(page: ReplayPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (page) {
            is ReplayPage.Intro -> Headline(stringResource(R.string.wrapped_title, page.year))

            is ReplayPage.Totals -> {
                Caption(stringResource(R.string.wrapped_total_plays))
                Headline(pluralStringResource(R.plurals.play_count, page.plays, page.plays))
                if (page.listeningTimeMs > 0) {
                    val minutes = page.listeningTimeMs / 60_000
                    Spacer(Modifier.height(32.dp))
                    Caption(stringResource(R.string.wrapped_listening_time))
                    Headline(stringResource(R.string.wrapped_hours_minutes, minutes / 60, minutes % 60))
                }
            }

            is ReplayPage.TopArtist -> {
                Caption(stringResource(R.string.wrapped_top_artist))
                Headline(page.name)
                Caption(pluralStringResource(R.plurals.play_count, page.plays, page.plays))
            }

            is ReplayPage.TopTrack -> {
                Caption(stringResource(R.string.wrapped_top_track))
                Spacer(Modifier.height(16.dp))
                SquareImage(
                    uri = page.artwork,
                    modifier = Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    cornerRadius = 12.dp,
                )
                Spacer(Modifier.height(20.dp))
                Headline(page.title)
                Caption(page.artist)
                Caption(pluralStringResource(R.plurals.play_count, page.plays, page.plays))
            }

            is ReplayPage.ActiveDay -> {
                Caption(stringResource(R.string.wrapped_most_active_day))
                Headline(page.day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)))
                Caption(pluralStringResource(R.plurals.play_count, page.plays, page.plays))
            }
        }
    }
}

@Composable
private fun Headline(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineLarge.copy(fontSize = 34.sp, lineHeight = 40.sp),
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
        maxLines = 4,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

/** Label and value, resolved here because the poster is drawn outside composition. */
@Composable
private fun replayPosterRows(wrapped: WrappedStats): List<Pair<String, String>> = buildList {
    add(
        stringResource(R.string.wrapped_total_plays) to
            pluralStringResource(R.plurals.play_count, wrapped.totalPlays, wrapped.totalPlays)
    )
    if (wrapped.listeningTimeMs > 0) {
        val minutes = wrapped.listeningTimeMs / 60_000
        add(
            stringResource(R.string.wrapped_listening_time) to
                stringResource(R.string.wrapped_hours_minutes, minutes / 60, minutes % 60)
        )
    }
    wrapped.topArtist?.let { add(stringResource(R.string.wrapped_top_artist) to it.artist) }
    wrapped.topTrack?.let { add(stringResource(R.string.wrapped_top_track) to it.title) }
    wrapped.mostActiveDay?.let {
        add(
            stringResource(R.string.wrapped_most_active_day) to
                it.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
        )
    }
}

/** The year as one image. Plain canvas drawing, so it needs neither a screen nor a composition. */
internal object ReplayPoster {
    const val WIDTH = 1080
    const val HEIGHT = 1350
    private const val MARGIN = 88
    private const val BRAND = "Jelyvo"

    fun render(title: String, rows: List<Pair<String, String>>): Bitmap {
        val bitmap = createBitmap(WIDTH, HEIGHT)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.rgb(16, 16, 18))
        val inner = WIDTH - MARGIN * 2

        var y = MARGIN.toFloat()
        y += draw(canvas, title, 76f, true, android.graphics.Color.WHITE, inner, y, maxLines = 2) + 56f
        rows.forEach { (label, value) ->
            y += draw(canvas, label, 34f, false, android.graphics.Color.rgb(170, 170, 176), inner, y, maxLines = 1) + 8f
            y += draw(canvas, value, 52f, true, android.graphics.Color.WHITE, inner, y, maxLines = 2) + 34f
        }

        val brand = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(170, 170, 176)
            textSize = 38f
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText(BRAND, MARGIN.toFloat(), HEIGHT - MARGIN.toFloat(), brand)
        return bitmap
    }

    private fun draw(
        canvas: Canvas,
        text: String,
        size: Float,
        bold: Boolean,
        color: Int,
        width: Int,
        top: Float,
        maxLines: Int,
    ): Float {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(maxLines)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(MARGIN.toFloat(), top)
        layout.draw(canvas)
        canvas.restore()
        return layout.height.toFloat()
    }
}
