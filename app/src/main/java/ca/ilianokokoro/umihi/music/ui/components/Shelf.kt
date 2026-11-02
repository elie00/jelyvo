/*
 * Measurements and card layout adapted from BitChord's Shelf and ShelfCard
 * (https://github.com/kushagrasinghx/BitChord), GPL-3.0.
 * Copyright (C) Kushagra Singh and BitChord contributors.
 */
package ca.ilianokokoro.umihi.music.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** The margin every page keeps from the screen's edge. */
val PageGutter = 16.dp

private val ShelfCardWidth = 150.dp
private val ShelfCardCorner = 12.dp
private val ShelfCardGap = 14.dp

/** A shelf's title. Modest on purpose: on these pages the artwork carries the weight. */
@Composable
fun ShelfHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PageGutter, vertical = 10.dp),
    )
}

/** A titled row of cards that scrolls past the page's margins. */
@Composable
fun Shelf(
    title: String?,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        if (!title.isNullOrBlank()) ShelfHeader(title)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(ShelfCardGap),
            contentPadding = PaddingValues(horizontal = PageGutter),
            content = content,
        )
    }
}

/**
 * One card of a shelf: square artwork over a title and a credit.
 *
 * @param circular draws the artwork as a circle, which is how an artist reads apart from a release.
 * @param belowArtwork anything between the artwork and the text, such as a progress bar.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShelfCard(
    title: String,
    subtitle: String,
    artworkUri: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    circular: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    belowArtwork: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .width(ShelfCardWidth)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        SquareImage(
            uri = artworkUri,
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = if (circular) ShelfCardWidth / 2 else ShelfCardCorner,
        )
        belowArtwork()
        Spacer(Modifier.height(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle.isNotBlank()) {
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
