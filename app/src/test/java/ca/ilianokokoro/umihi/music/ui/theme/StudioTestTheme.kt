package ca.ilianokokoro.umihi.music.ui.theme

import androidx.compose.runtime.Composable

/** The Studio theme as the app applies it, for tests that render screens to images. */
@Composable
fun StudioTestTheme(dark: Boolean, content: @Composable () -> Unit) {
    UmihiMusicTheme(
        darkTheme = dark,
        themePreset = ThemePreset.Studio,
        fontFamily = nunitoFontFamily(),
        studioFontFamily = dmSansFontFamily(),
        content = content,
    )
}
