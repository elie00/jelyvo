package ca.ilianokokoro.umihi.music.desktop.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

/**
 * Nunito on macOS, from the variable font Gradle copies out of `music-ui/fonts` onto the classpath.
 *
 * Every entry names the same file; Skia applies the weight axis, so the eight cuts the type scale
 * asks for all resolve from one 270 KB resource.
 */
private const val FONT_RESOURCE = "font/nunito.ttf"

private val WEIGHTS = listOf(
    FontWeight.ExtraLight,
    FontWeight.Light,
    FontWeight.Normal,
    FontWeight.Medium,
    FontWeight.SemiBold,
    FontWeight.Bold,
    FontWeight.ExtraBold,
    FontWeight.Black,
)

val nunitoFontFamily: FontFamily = FontFamily(
    WEIGHTS.map { weight -> Font(FONT_RESOURCE, weight, FontStyle.Normal) }
)
