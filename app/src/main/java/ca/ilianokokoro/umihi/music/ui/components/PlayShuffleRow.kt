package ca.ilianokokoro.umihi.music.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.ui.components.materialu.dropdown.MaterialUDropdown
import ca.ilianokokoro.umihi.music.ui.components.materialu.dropdown.MaterialUDropdownItem

/** "Play" / "Shuffle" actions shown at the top of an album or artist page. */
@Composable
fun PlayShuffleRow(
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        // Play — START segment, filled container colors
        Button(
            onClick = onPlay,
            shape = ButtonGroupDefaults.connectedLeadingButtonShape,
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
        ) {
            PlayShuffleButtonContent(
                icon = Icons.Rounded.PlayArrow,
                text = stringResource(R.string.play),
            )
        }

        // Shuffle — END segment, tonal container colors
        FilledTonalButton(
            onClick = onShuffle,
            shape = ButtonGroupDefaults.connectedTrailingButtonShape,
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
        ) {
            PlayShuffleButtonContent(
                icon = Icons.Rounded.Shuffle,
                text = stringResource(R.string.shuffle),
            )
        }
    }
}

/**
 * "Play" action with an attached menu of secondary actions. Not wired into any
 * screen yet; kept for upcoming header revisions.
 */
@Composable
fun PlaySplitButton(
    onPlay: () -> Unit,
    menuActions: List<PlaySplitButtonAction>,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    SplitButtonLayout(
        leadingButton = {
            SplitButtonDefaults.LeadingButton(onClick = onPlay) {
                PlayShuffleButtonContent(
                    icon = Icons.Rounded.PlayArrow,
                    text = stringResource(R.string.play),
                )
            }
        },
        trailingButton = {
            SplitButtonDefaults.TrailingButton(
                checked = menuExpanded,
                onCheckedChange = { menuExpanded = it },
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.actions),
                )

                MaterialUDropdown(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    menuActions.forEach { action ->
                        MaterialUDropdownItem(
                            text = action.label,
                            leadingIcon = action.icon,
                            onClick = {
                                action.onClick()
                                menuExpanded = false
                            },
                        )
                    }
                }
            }
        },
        modifier = modifier,
    )
}

data class PlaySplitButtonAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
private fun PlayShuffleButtonContent(
    icon: ImageVector,
    text: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
        )
    }
}
