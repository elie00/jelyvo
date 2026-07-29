package ca.ilianokokoro.umihi.music.ui.components

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.helpers.ArtworkUrl
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

@Composable
fun SquareImage(
    modifier: Modifier = Modifier,
    uri: String,
    contentDescription: String? = null,
    cornerRadius: Dp = 12.dp,
    requestSize: Dp? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val requestSizePx = remember(requestSize, density) {
        requestSize?.let { with(density) { it.roundToPx() } }
    }
    val request = remember(uri, requestSizePx) {
        ImageRequest.Builder(context)
            .data(requestSizePx?.let { ArtworkUrl.thumbnail(uri, it) } ?: uri)
            .apply {
                requestSizePx?.let { size(it, it) }
            }
            .crossfade(Constants.Animation.IMAGE_FADE_DURATION)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(cornerRadius)),
        error = ColorPainter(MaterialTheme.colorScheme.surfaceContainerHighest),
        placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}
