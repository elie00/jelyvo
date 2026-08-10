package ca.ilianokokoro.umihi.music.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/** macOS exposes an accent colour but no Material You palette, so Jelyvo's own scheme stands. */
@Composable
actual fun platformDynamicColorScheme(darkTheme: Boolean): ColorScheme? = null
