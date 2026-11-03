/*
 * Adapted from BitChord's PlayerDrawer, ActionRow, SheetTrackHeader and SheetHeading
 * (https://github.com/kushagrasinghx/BitChord), GPL-3.0.
 * Copyright (C) Kushagra Singh and BitChord contributors.
 */
package ca.ilianokokoro.umihi.music.ui.components.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet as MaterialModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val SHEET_SHAPE = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)

/** Rows are a label and a mark: stretched across a tablet each is a word, a gap and a dot. */
val SHEET_MAX_WIDTH = 640.dp

private val SCRIM = Color.Black.copy(alpha = 0.5f)
private const val DISABLED_ROW_ALPHA = 0.4f

/**
 * A sheet that opens all the way or not at all, with a thin grab handle and an optional title.
 * Takes Material's call shape, so a screen adopts it by import alone. Material still does the window,
 * the drag and the back gesture, which are the parts worth not re-learning.
 *
 * No frost here, unlike BitChord's drawer: this sheet has a window of its own, and the blur has
 * nothing recorded to read from across a window boundary. The solid colour is the same as BitChord
 * draws when blur is turned off.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    MaterialModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
        sheetMaxWidth = SHEET_MAX_WIDTH,
        shape = SHEET_SHAPE,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        scrimColor = SCRIM,
        dragHandle = { SheetHandle() },
    ) {
        title?.let { SheetTitle(it) }
        content()
    }
}

/** The sheet without its window: one of its own cannot be captured in a test. */
@Composable
internal fun SheetCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(SHEET_SHAPE)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            SheetHandle()
            title?.let { SheetTitle(it) }
            content()
        }
    }
}

@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(top = 10.dp, bottom = 12.dp)
            .size(width = 36.dp, height = 4.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)),
    )
}

@Composable
@ReadOnlyComposable
fun sheetTitleStyle(): TextStyle = MaterialTheme.typography.titleLarge.copy(
    fontSize = 19.sp,
    fontWeight = FontWeight.Bold,
)

@Composable
fun SheetTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = sheetTitleStyle(),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 14.dp),
    )
}

/** What the sheet is about: a track or a release, with its artwork when it has one. */
@Composable
fun SheetHeader(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    artwork: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        artwork?.let {
            it()
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The small heading over a group of rows. */
@Composable
fun SheetHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 4.dp),
    )
}

/**
 * An icon and a label across the sheet. [description] sits under the label, [value] at the far end,
 * [tint] colours both the icon and the label for a state that is on. No [onClick] means the row only
 * informs, and it then neither ripples nor reads as a button.
 */
@Composable
fun SheetActionRow(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    value: String? = null,
    tint: Color? = null,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val alpha = if (enabled) 1f else DISABLED_ROW_ALPHA
    val foreground = tint ?: MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 22.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = foreground.copy(alpha = alpha),
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = foreground.copy(alpha = alpha),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (value != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                maxLines = 1,
            )
        }
        trailing?.let {
            Spacer(Modifier.width(12.dp))
            it()
        }
    }
}
