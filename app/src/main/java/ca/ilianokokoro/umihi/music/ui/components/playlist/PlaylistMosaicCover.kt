package ca.ilianokokoro.umihi.music.ui.components.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.models.MOSAIC_COVER_COUNT
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * Cover built from the first tracks of a playlist that has no artwork of its own (on-device
 * playlists). Degrades to a 2x1 split, an L-shape and a single image as covers run out, and to a
 * plain surface block when [uris] is empty — the same flat placeholder a coverless playlist shows
 * today.
 */
@Composable
fun PlaylistMosaicCover(
    uris: List<String>,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    cornerRadius: Dp = 12.dp,
) {
    val tiles = uris.take(MOSAIC_COVER_COUNT)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        when (tiles.size) {
            // Nothing to draw: the background above is the placeholder.
            0 -> Unit

            1 -> MosaicTile(tiles[0], Modifier.fillMaxSize(), contentDescription)

            2 -> Row(Modifier.fillMaxSize()) {
                MosaicTile(
                    tiles[0],
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentDescription
                )
                MosaicTile(
                    tiles[1],
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }

            3 -> Row(Modifier.fillMaxSize()) {
                MosaicTile(
                    tiles[0],
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentDescription
                )
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    MosaicTile(
                        tiles[1],
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                    MosaicTile(
                        tiles[2],
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }

            else -> Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    MosaicTile(
                        tiles[0],
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentDescription
                    )
                    MosaicTile(
                        tiles[1],
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    MosaicTile(
                        tiles[2],
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    MosaicTile(
                        tiles[3],
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

/** One tile of the mosaic. Cropped to fill so tiles keep a common grid regardless of source ratio. */
@Composable
private fun MosaicTile(
    uri: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val context = LocalContext.current
    val request = remember(uri) {
        ImageRequest.Builder(context)
            .data(uri)
            .crossfade(Constants.Animation.IMAGE_FADE_DURATION)
            .build()
    }

    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}
