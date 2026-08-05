package ca.ilianokokoro.umihi.music.ui.screens.stats.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.drawText
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields

private const val WEEK_COLUMNS = 26
private const val DAY_ROWS = 7

/**
 * GitHub-style listening activity heatmap: one cell per day over the last [WEEK_COLUMNS] weeks,
 * cell intensity derived from the play count of that day, with month labels on top.
 */
@Composable
fun ListeningHeatmap(
    dailyCounts: Map<LocalDate, Int>,
    modifier: Modifier = Modifier,
) {
    val cellColor = MaterialTheme.colorScheme.primary
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall
    val textMeasurer = rememberTextMeasurer()

    val today = LocalDate.now()
    // Locale.getDefault() is the system locale and is not observed by Compose: with a per-app
    // language set, the week start and the month names could both be wrong and never update.
    val locale = LocalConfiguration.current.locales[0]
    val firstDayOfWeek: DayOfWeek = WeekFields.of(locale).firstDayOfWeek
    val currentWeekStart = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
    val firstWeekStart = currentWeekStart.minusWeeks((WEEK_COLUMNS - 1).toLong())
    val maxCount = dailyCounts.values.maxOrNull()?.coerceAtLeast(1) ?: 1

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val gap = 2.dp
        val cell = (maxWidth - gap * (WEEK_COLUMNS - 1)) / WEEK_COLUMNS
        val labelHeight = 18.dp
        val gridHeight = labelHeight + cell * DAY_ROWS + gap * (DAY_ROWS - 1)

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(gridHeight),
        ) {
            val cellPx = cell.toPx()
            val gapPx = gap.toPx()
            val labelHeightPx = labelHeight.toPx()
            val corner = CornerRadius(cellPx / 4f, cellPx / 4f)

            var lastLabeledMonth = -1
            var lastLabelEndX = Float.NEGATIVE_INFINITY

            for (week in 0 until WEEK_COLUMNS) {
                val weekStart = firstWeekStart.plusWeeks(week.toLong())
                val x = week * (cellPx + gapPx)

                // Month label above the first column of each new month.
                if (weekStart.monthValue != lastLabeledMonth) {
                    lastLabeledMonth = weekStart.monthValue
                    val label = weekStart.month.getDisplayName(
                        java.time.format.TextStyle.SHORT,
                        locale,
                    )
                    val measured = textMeasurer.measure(label, labelStyle)
                    if (x > lastLabelEndX + gapPx) {
                        drawText(
                            textLayoutResult = measured,
                            color = labelColor,
                            topLeft = Offset(x, 0f),
                        )
                        lastLabelEndX = x + measured.size.width
                    }
                }

                for (day in 0 until DAY_ROWS) {
                    val date = weekStart.plusDays(day.toLong())
                    if (date.isAfter(today)) continue

                    val count = dailyCounts[date] ?: 0
                    val color = cellColorFor(count, maxCount, cellColor, emptyColor)
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, labelHeightPx + day * (cellPx + gapPx)),
                        size = Size(cellPx, cellPx),
                        cornerRadius = corner,
                    )
                }
            }
        }
    }
}

private fun cellColorFor(count: Int, maxCount: Int, base: Color, empty: Color): Color {
    if (count <= 0) return empty.copy(alpha = 0.6f)

    val ratio = count.toFloat() / maxCount
    val alpha = when {
        ratio <= 0.25f -> 0.3f
        ratio <= 0.5f -> 0.5f
        ratio <= 0.75f -> 0.75f
        else -> 1f
    }
    return base.copy(alpha = alpha)
}
