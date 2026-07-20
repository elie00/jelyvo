package ca.ilianokokoro.umihi.music.ui.screens.playlist.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.ui.components.PlaySplitButton
import ca.ilianokokoro.umihi.music.ui.components.PlaySplitButtonAction


@Composable
fun ActionButtons(
    onPlayClicked: () -> Unit,
    onShuffleClicked: () -> Unit,
    onRadioClicked: () -> Unit,
    onDownloadClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    PlaySplitButton(
        onPlay = onPlayClicked,
        menuActions = listOf(
            PlaySplitButtonAction(
                label = stringResource(R.string.shuffle),
                icon = Icons.Rounded.Shuffle,
                onClick = onShuffleClicked,
            ),
            PlaySplitButtonAction(
                label = stringResource(R.string.start_radio),
                icon = Icons.Rounded.Radio,
                onClick = onRadioClicked,
            ),
            PlaySplitButtonAction(
                label = stringResource(R.string.download),
                icon = Icons.Rounded.Download,
                onClick = onDownloadClicked,
            ),
        ),
        modifier = modifier,
    )
}
