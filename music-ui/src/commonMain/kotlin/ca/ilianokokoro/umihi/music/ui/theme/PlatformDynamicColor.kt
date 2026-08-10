package ca.ilianokokoro.umihi.music.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/**
 * The platform's own palette, when it has one. Android exposes Material You from API 31; every
 * other client returns null and the caller falls back to Jelyvo's own scheme.
 */
@Composable
expect fun platformDynamicColorScheme(darkTheme: Boolean): ColorScheme?
