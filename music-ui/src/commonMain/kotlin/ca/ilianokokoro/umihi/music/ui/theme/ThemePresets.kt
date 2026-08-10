package ca.ilianokokoro.umihi.music.ui.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * Themes complets, par opposition aux simples accents de [SeedSchemes] : chaque preset engage a la
 * fois les couleurs (surfaces comprises), la typographie et le langage de formes. Jelyvo reste le
 * theme de la marque et conserve le selecteur d'accent ; les autres presets imposent le leur.
 *
 * - [ThemePreset.Minuit] : noir OLED absolu, accents bleu glacier, sans-serif systeme, coins tres
 *   arrondis. Pense pour l'ecoute nocturne et l'economie d'ecran AMOLED.
 * - [ThemePreset.Vinyle] : creme sepia et brun chaud, titrages serif, coins courts — l'esthetique
 *   d'une pochette des annees 70.
 * - [ThemePreset.Neon] : violet nuit, magenta electrique et cyan, coins coupes — l'enseigne au neon.
 */
object ThemePresets {

    /** Schema de couleurs du preset, ou null pour Jelyvo (gere par le theme de marque). */
    fun colorScheme(preset: ThemePreset, darkTheme: Boolean): ColorScheme? = when (preset) {
        ThemePreset.Jelyvo -> null
        ThemePreset.Minuit -> minuitScheme(darkTheme)
        ThemePreset.Vinyle -> vinyleScheme(darkTheme)
        ThemePreset.Neon -> neonScheme(darkTheme)
        ThemePreset.Oled -> oledScheme(darkTheme)
    }

    /** Typographie du preset ; Jelyvo garde [appTypography] (Nunito). */
    @Composable
    fun typography(preset: ThemePreset, fontFamily: FontFamily): Typography = when (preset) {
        ThemePreset.Jelyvo -> appTypography(fontFamily)
        ThemePreset.Minuit -> withFontFamily(FontFamily.SansSerif)
        ThemePreset.Vinyle -> withDisplayFontFamily(FontFamily.Serif, fontFamily)
        ThemePreset.Neon -> appTypography(fontFamily)
        ThemePreset.Oled -> appTypography(fontFamily)
    }

    /** Langage de formes du preset, ou null pour les formes expressives par defaut. */
    fun shapes(preset: ThemePreset): Shapes? = when (preset) {
        ThemePreset.Jelyvo -> null
        ThemePreset.Minuit -> Shapes(
            extraSmall = RoundedCornerShape(12.dp),
            small = RoundedCornerShape(16.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp),
            extraLarge = RoundedCornerShape(36.dp),
        )

        ThemePreset.Vinyle -> Shapes(
            extraSmall = RoundedCornerShape(2.dp),
            small = RoundedCornerShape(4.dp),
            medium = RoundedCornerShape(6.dp),
            large = RoundedCornerShape(10.dp),
            extraLarge = RoundedCornerShape(14.dp),
        )

        ThemePreset.Neon -> Shapes(
            extraSmall = CutCornerShape(4.dp),
            small = CutCornerShape(6.dp),
            medium = CutCornerShape(10.dp),
            large = CutCornerShape(14.dp),
            extraLarge = CutCornerShape(20.dp),
        )

        ThemePreset.Oled -> null
    }

    /** Trio (fond, primaire, tertiaire) pour la vignette du selecteur. */
    fun previewColors(preset: ThemePreset, darkTheme: Boolean): Triple<Color, Color, Color> =
        when (preset) {
            ThemePreset.Jelyvo ->
                if (darkTheme) {
                    Triple(Color(0xFF1A1113), Color(0xFFFFB4A8), Color(0xFFE7BDB4))
                } else {
                    Triple(Color(0xFFFFF8ED), Color(0xFFA53F32), Color(0xFF74304F))
                }

            else -> {
                val scheme = colorScheme(preset, darkTheme)!!
                Triple(scheme.background, scheme.primary, scheme.tertiary)
            }
        }

    // ------------------------------------------------------------------ Minuit

    private fun minuitScheme(darkTheme: Boolean): ColorScheme {
        val base = SeedSchemes.colorScheme(hue = 222f, chroma = 0.5f, darkTheme = darkTheme)
        if (!darkTheme) return base

        // Le noir absolu des surfaces est la signature du preset : chaque conteneur remonte a
        // peine au-dessus, teinte du meme bleu que l'accent pour rester coherent.
        return base.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceDim = Color.Black,
            surfaceBright = Color(0xFF22232B),
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF08080B),
            surfaceContainer = Color(0xFF0E0E13),
            surfaceContainerHigh = Color(0xFF14141B),
            surfaceContainerHighest = Color(0xFF1A1B23),
        )
    }

    // ------------------------------------------------------------------ Vinyle

    private fun vinyleScheme(darkTheme: Boolean): ColorScheme {
        val base = SeedSchemes.colorScheme(hue = 30f, chroma = 0.62f, darkTheme = darkTheme)
        return if (darkTheme) {
            // Brun profond plutot que gris : le neutre du seed est trop froid pour du sepia.
            base.copy(
                background = Color(0xFF17100A),
                surface = Color(0xFF17100A),
                surfaceContainerLowest = Color(0xFF120C07),
                surfaceContainerLow = Color(0xFF1D150E),
                surfaceContainer = Color(0xFF231A12),
                surfaceContainerHigh = Color(0xFF2A2017),
                surfaceContainerHighest = Color(0xFF31261C),
            )
        } else {
            base.copy(
                background = Color(0xFFFBF3E4),
                surface = Color(0xFFFBF3E4),
                surfaceContainerLowest = Color(0xFFFFFDF6),
                surfaceContainerLow = Color(0xFFF6ECDA),
                surfaceContainer = Color(0xFFF1E5CF),
                surfaceContainerHigh = Color(0xFFEBDEC5),
                surfaceContainerHighest = Color(0xFFE5D7BB),
            )
        }
    }

    // -------------------------------------------------------------------- Neon

    private fun neonScheme(darkTheme: Boolean): ColorScheme {
        val base = SeedSchemes.colorScheme(hue = 315f, chroma = 0.9f, darkTheme = darkTheme)
        // Le tertiaire derive du seed tombe dans l'orange ; l'enseigne veut du cyan.
        val cyan = SeedSchemes.colorScheme(hue = 190f, chroma = 0.85f, darkTheme = darkTheme)
        val withCyan = base.copy(
            tertiary = cyan.primary,
            onTertiary = cyan.onPrimary,
            tertiaryContainer = cyan.primaryContainer,
            onTertiaryContainer = cyan.onPrimaryContainer,
        )
        if (!darkTheme) return withCyan

        // Fond violet-noir : plus sombre que le neutre du seed, mais jamais neutre.
        return withCyan.copy(
            background = Color(0xFF0C0714),
            surface = Color(0xFF0C0714),
            surfaceContainerLowest = Color(0xFF080510),
            surfaceContainerLow = Color(0xFF120B1D),
            surfaceContainer = Color(0xFF181026),
            surfaceContainerHigh = Color(0xFF1F152F),
            surfaceContainerHighest = Color(0xFF261B38),
        )
    }

    // -------------------------------------------------------------------- Oled

    private fun oledScheme(darkTheme: Boolean): ColorScheme {
        val base = SeedSchemes.colorScheme(hue = 15f, chroma = 0.7f, darkTheme = darkTheme)
        if (!darkTheme) return base

        return base.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceDim = Color.Black,
            surfaceBright = Color(0xFF161616),
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF060606),
            surfaceContainer = Color(0xFF0C0C0C),
            surfaceContainerHigh = Color(0xFF121212),
            surfaceContainerHighest = Color(0xFF1A1A1A),
        )
    }

    // ------------------------------------------------------------------ helpers

    /** [appTypography] avec toutes les familles remplacees. */
    @Composable
    private fun withFontFamily(family: FontFamily): Typography = appTypography(family).run {
        Typography(
            displayLarge = displayLarge.copy(fontFamily = family),
            displayMedium = displayMedium.copy(fontFamily = family),
            displaySmall = displaySmall.copy(fontFamily = family),
            headlineLarge = headlineLarge.copy(fontFamily = family),
            headlineMedium = headlineMedium.copy(fontFamily = family),
            headlineSmall = headlineSmall.copy(fontFamily = family),
            titleLarge = titleLarge.copy(fontFamily = family),
            titleMedium = titleMedium.copy(fontFamily = family),
            titleSmall = titleSmall.copy(fontFamily = family),
            bodyLarge = bodyLarge.copy(fontFamily = family),
            bodyMedium = bodyMedium.copy(fontFamily = family),
            bodySmall = bodySmall.copy(fontFamily = family),
            labelLarge = labelLarge.copy(fontFamily = family),
            labelMedium = labelMedium.copy(fontFamily = family),
            labelSmall = labelSmall.copy(fontFamily = family),
        )
    }

    /**
     * [appTypography] avec les titrages (display/headline/title) en [family], le corps de texte
     * restant en Nunito : le serif fait la pochette, pas les listes.
     */
    @Composable
    private fun withDisplayFontFamily(family: FontFamily, bodyFamily: FontFamily): Typography = appTypography(bodyFamily).run {
        Typography(
            displayLarge = displayLarge.copy(fontFamily = family),
            displayMedium = displayMedium.copy(fontFamily = family),
            displaySmall = displaySmall.copy(fontFamily = family),
            headlineLarge = headlineLarge.copy(fontFamily = family),
            headlineMedium = headlineMedium.copy(fontFamily = family),
            headlineSmall = headlineSmall.copy(fontFamily = family),
            titleLarge = titleLarge.copy(fontFamily = family),
            titleMedium = titleMedium.copy(fontFamily = family),
            titleSmall = titleSmall.copy(fontFamily = family),
            bodyLarge = bodyLarge,
            bodyMedium = bodyMedium,
            bodySmall = bodySmall,
            labelLarge = labelLarge,
            labelMedium = labelMedium,
            labelSmall = labelSmall,
        )
    }
}
