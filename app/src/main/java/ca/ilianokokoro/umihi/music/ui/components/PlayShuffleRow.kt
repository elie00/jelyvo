/*
 * Layout and measures adapted from BitChord's release actions
 * (https://github.com/kushagrasinghx/BitChord), GPL-3.0.
 * Copyright (C) Kushagra Singh and BitChord contributors.
 */
package ca.ilianokokoro.umihi.music.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.ui.components.materialu.dropdown.MaterialUDropdown
import ca.ilianokokoro.umihi.music.ui.components.materialu.dropdown.MaterialUDropdownItem
import ca.ilianokokoro.umihi.music.ui.components.studio.ActionPill
import ca.ilianokokoro.umihi.music.ui.components.studio.CircleIconButton
import ca.ilianokokoro.umihi.music.ui.components.studio.RELEASE_ACTION_GAP

private val RELEASE_ACTIONS_MAX_WIDTH = 420.dp

/** Play and Shuffle side by side, for what is already on this device and has no library to join. */
@Composable
fun PlayShuffleRow(
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(RELEASE_ACTION_GAP),
    ) {
        ActionPill(
            icon = Icons.Rounded.PlayArrow,
            label = stringResource(R.string.play),
            onClick = onPlay,
            filled = true,
            modifier = Modifier.weight(1f),
        )
        ActionPill(
            icon = Icons.Rounded.Shuffle,
            label = stringResource(R.string.shuffle),
            onClick = onShuffle,
            filled = false,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Shuffle, Play across what is left, then whatever else the page offers, in round buttons. */
@Composable
fun ReleaseActionRow(
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    menuActions: List<PlaySplitButtonAction>,
    modifier: Modifier = Modifier,
    saved: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .widthIn(max = RELEASE_ACTIONS_MAX_WIDTH)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(RELEASE_ACTION_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(
            icon = Icons.Rounded.Shuffle,
            contentDescription = stringResource(R.string.shuffle),
            onClick = onShuffle,
        )
        ActionPill(
            icon = Icons.Rounded.PlayArrow,
            label = stringResource(R.string.play),
            onClick = onPlay,
            filled = true,
            modifier = Modifier.weight(1f),
        )
        saved?.invoke()
        if (menuActions.isNotEmpty()) ReleaseMenuButton(menuActions)
    }
}

@Composable
private fun ReleaseMenuButton(actions: List<PlaySplitButtonAction>) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        CircleIconButton(
            icon = Icons.Rounded.MoreHoriz,
            contentDescription = stringResource(R.string.actions),
            onClick = { expanded = true },
        )
        MaterialUDropdown(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            actions.forEach { action ->
                MaterialUDropdownItem(
                    text = action.label,
                    leadingIcon = action.icon,
                    onClick = {
                        action.onClick()
                        expanded = false
                    },
                )
            }
        }
    }
}

data class PlaySplitButtonAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)
