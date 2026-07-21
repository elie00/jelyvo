package ca.ilianokokoro.umihi.music.ui.components.song

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.ui.components.SquareImage
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * Row thumbnail of a track: square album cover for songs, wide 16:9 rounded poster for video
 * results ([Song.isVideo]) — mirroring YT Music's differentiated song/video rows.
 */
@Composable
fun SongThumbnail(
    song: Song,
    modifier: Modifier = Modifier,
    size: Dp = 60.dp,
) {
    val uri = song.thumbnailPath ?: song.thumbnailHref
    if (song.isVideo) {
        val context = LocalContext.current
        val request = remember(uri) {
            ImageRequest.Builder(context).data(uri)
                .crossfade(Constants.Animation.IMAGE_FADE_DURATION).build()
        }
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .height(size)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp)),
            error = ColorPainter(MaterialTheme.colorScheme.surfaceContainerHighest),
            placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .aspectRatio(1f)
        ) {
            SquareImage(
                uri = uri,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}
