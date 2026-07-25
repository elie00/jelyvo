package ca.ilianokokoro.umihi.music.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Reorder
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.models.SongSort
import ca.ilianokokoro.umihi.music.ui.components.materialu.dropdown.MaterialUDropdown
import ca.ilianokokoro.umihi.music.ui.components.materialu.dropdown.MaterialUDropdownItem

/** Single-choice sort menu, shared by the remote and the on-device playlist screens. */
@Composable
fun SongSortButton(
    sort: SongSort,
    onSortChange: (SongSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(
        onClick = { expanded = true },
        modifier = modifier,
        shapes = IconButtonDefaults.shapes(),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.Sort,
            contentDescription = stringResource(R.string.sort_by),
        )

        MaterialUDropdown(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            SongSort.entries.forEach { option ->
                MaterialUDropdownItem(
                    // A tick on the active axis: the menu is the only place the current sort shows.
                    leadingIcon = if (option == sort) Icons.Rounded.Check else option.icon(),
                    text = stringResource(option.labelRes()),
                    onClick = {
                        onSortChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun SongSort.icon() = when (this) {
    SongSort.PLAYLIST_ORDER -> Icons.Rounded.Reorder
    SongSort.TITLE -> Icons.Rounded.Title
    SongSort.ARTIST -> Icons.Rounded.Person
    SongSort.DURATION -> Icons.Rounded.AccessTime
}

private fun SongSort.labelRes() = when (this) {
    SongSort.PLAYLIST_ORDER -> R.string.sort_playlist_order
    SongSort.TITLE -> R.string.sort_title
    SongSort.ARTIST -> R.string.sort_artist
    SongSort.DURATION -> R.string.sort_duration
}
