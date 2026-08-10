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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.core.helpers.ArtworkUrl
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade

/** Crossfade length shared by every artwork surface, so lists settle at one rhythm. */
const val ARTWORK_FADE_DURATION_MS = 200

/**
 * Square album artwork, as the phone draws it: rounded, cropped, with the surface colour standing
 * in while it loads or when it fails.
 *
 * [requestSize] rewrites Google-hosted URLs to the exact pixel size the row needs — list rows only
 * ever show 60 dp, and fetching player-sized artwork for them wastes bandwidth and decode time.
 */
@Composable
fun SquareArtwork(
    uri: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    cornerRadius: Dp = 12.dp,
    requestSize: Dp? = null,
) {
    val context = LocalPlatformContext.current
    val density = LocalDensity.current
    val requestSizePx = remember(requestSize, density) {
        requestSize?.let { with(density) { it.roundToPx() } }
    }
    val request = remember(uri, requestSizePx, context) {
        ImageRequest.Builder(context)
            .data(requestSizePx?.let { ArtworkUrl.thumbnail(uri, it) } ?: uri)
            .apply { requestSizePx?.let { size(it, it) } }
            .crossfade(ARTWORK_FADE_DURATION_MS)
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
