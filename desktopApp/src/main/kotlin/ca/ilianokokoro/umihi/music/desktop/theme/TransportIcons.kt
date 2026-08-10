package ca.ilianokokoro.umihi.music.desktop.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The four transport glyphs, transcribed from Material's own 24 dp path data.
 *
 * Compose Multiplatform stopped bundling the icon set, and its extended artifact carries the whole
 * catalogue — several thousand icons — to serve these four. The shapes are identical to the ones
 * the phone draws; only their provenance differs.
 */
object TransportIcons {
    val PlayArrow: ImageVector = materialIcon("PlayArrow", "M8 5v14l11-7z")
    val Pause: ImageVector = materialIcon("Pause", "M6 19h4V5H6v14zm8-14v14h4V5h-4z")
    val SkipNext: ImageVector = materialIcon("SkipNext", "M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z")
    val SkipPrevious: ImageVector = materialIcon("SkipPrevious", "M6 6h2v12H6zm3.5 6l8.5 6V6z")
}

private fun materialIcon(name: String, pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(pathData),
        fill = SolidColor(Color.Black),
    ).build()
