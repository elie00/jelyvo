package ca.ilianokokoro.umihi.music.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import ca.ilianokokoro.umihi.music.R

/**
 * Nunito on Android, from the variable font Gradle copies out of `music-ui/fonts`.
 *
 * Every entry points at the same file: the weight axis produces the eight cuts that used to be
 * eight separate downloadable-font declarations, which failed silently without Play Services.
 */
@Composable
fun nunitoFontFamily(): FontFamily = FontFamily(
    Font(R.font.nunito, FontWeight.ExtraLight, FontStyle.Normal),
    Font(R.font.nunito, FontWeight.Light, FontStyle.Normal),
    Font(R.font.nunito, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.nunito, FontWeight.Medium, FontStyle.Normal),
    Font(R.font.nunito, FontWeight.SemiBold, FontStyle.Normal),
    Font(R.font.nunito, FontWeight.Bold, FontStyle.Normal),
    Font(R.font.nunito, FontWeight.ExtraBold, FontStyle.Normal),
    Font(R.font.nunito, FontWeight.Black, FontStyle.Normal),
)

/** DM Sans on Android, the Studio theme's typeface, delivered the same way as Nunito. */
@Composable
fun dmSansFontFamily(): FontFamily = FontFamily(
    Font(R.font.dm_sans, FontWeight.Light, FontStyle.Normal),
    Font(R.font.dm_sans, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.dm_sans, FontWeight.Medium, FontStyle.Normal),
    Font(R.font.dm_sans, FontWeight.SemiBold, FontStyle.Normal),
    Font(R.font.dm_sans, FontWeight.Bold, FontStyle.Normal),
    Font(R.font.dm_sans, FontWeight.ExtraBold, FontStyle.Normal),
    Font(R.font.dm_sans, FontWeight.Black, FontStyle.Normal),
)
