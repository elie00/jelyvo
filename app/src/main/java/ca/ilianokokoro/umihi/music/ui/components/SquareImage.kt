package ca.ilianokokoro.umihi.music.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Android's entry point to the shared artwork surface, kept so existing call sites read unchanged.
 * The drawing itself lives in :music-ui, so the phone and the desktop cannot diverge.
 */
@Composable
fun SquareImage(
    modifier: Modifier = Modifier,
    uri: String,
    contentDescription: String? = null,
    cornerRadius: Dp = 12.dp,
    requestSize: Dp? = null,
) = SquareArtwork(
    uri = uri,
    modifier = modifier,
    contentDescription = contentDescription,
    cornerRadius = cornerRadius,
    requestSize = requestSize,
)
