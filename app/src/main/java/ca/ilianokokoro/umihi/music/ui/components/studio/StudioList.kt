/*
 * Measures adapted from BitChord's SongRow and RowMoreButton
 * (https://github.com/kushagrasinghx/BitChord), GPL-3.0.
 * Copyright (C) Kushagra Singh and BitChord contributors.
 */
package ca.ilianokokoro.umihi.music.ui.components.studio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** The space between a row's contents and the edge of the page. */
val PAGE_GUTTER = 16.dp

/** Artwork in a row. With the row's padding it makes 60dp, the height every list here shares. */
val ROW_ART_SIZE = 52.dp

private val ROW_MORE_WIDTH = 40.dp
private val ROW_MORE_HEIGHT = 48.dp

/** How far [RowMoreButton] reaches into the gutter, so its dots end where the row's content ends. */
private val ROW_MORE_OUTSET = 16.dp

/**
 * A row in BitChord's measures: artwork, then a title and a dimmer line, with whatever else at the
 * far end. See-through, so a tinted page shows behind it. Takes Material's call shape, so a screen
 * adopts it by import alone.
 */
@Composable
fun ListItem(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .padding(horizontal = PAGE_GUTTER, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingContent?.let {
            it()
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                ProvideTextStyle(MaterialTheme.typography.titleMedium) { headlineContent() }
            }
            supportingContent?.let {
                Spacer(Modifier.height(2.dp))
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                    ProvideTextStyle(MaterialTheme.typography.bodyMedium) { it() }
                }
            }
        }
        trailingContent?.let {
            Spacer(Modifier.width(8.dp))
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                ProvideTextStyle(MaterialTheme.typography.labelMedium) { it() }
            }
        }
    }
}

/** The same row with its title last, as Material's other overload takes it. */
@Composable
fun ListItem(
    modifier: Modifier = Modifier,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) = ListItem(
    headlineContent = content,
    modifier = modifier,
    supportingContent = supportingContent,
    leadingContent = leadingContent,
    trailingContent = trailingContent,
)

/**
 * The ⋮ at the end of a row. Its circle is a target nobody sees, so it hangs into the gutter: the
 * dots end at the content's edge, mirroring the artwork on the left, and the text gets the width
 * back. A [menu] placed in [content] drops from it.
 */
@Composable
fun RowMoreButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier = modifier
            .hangIntoGutter()
            .size(ROW_MORE_WIDTH, ROW_MORE_HEIGHT)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.MoreVert,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        content()
    }
}

private fun Modifier.hangIntoGutter(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val width = (placeable.width - ROW_MORE_OUTSET.roundToPx()).coerceAtLeast(0)
    layout(width, placeable.height) { placeable.place(0, 0) }
}
