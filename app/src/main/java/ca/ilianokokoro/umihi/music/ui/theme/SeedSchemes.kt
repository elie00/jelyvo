package ca.ilianokokoro.umihi.music.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.ThemeColor

/**
 * Hand-built Material 3 color schemes generated from seed colors.
 *
 * User-selectable accent themes map to a seed hue. Jelyvo's legacy [ThemeColor.Umihi] value falls back to
 * the hand-tuned brand scheme in [Theme], while [ThemeColor.Dynamic] uses Material You.
 */
object SeedSchemes {

    private data class SeedSpec(
        val hue: Float,
        val chroma: Float = 0.55f
    )

    private val specs = mapOf(
        ThemeColor.OceanBlue to SeedSpec(hue = 212f),
        ThemeColor.ForestGreen to SeedSpec(hue = 130f, chroma = 0.45f),
        ThemeColor.Violet to SeedSpec(hue = 265f),
        ThemeColor.SunsetOrange to SeedSpec(hue = 26f, chroma = 0.65f),
        ThemeColor.RoseRed to SeedSpec(hue = 345f),
        ThemeColor.Mono to SeedSpec(hue = 260f, chroma = 0f)
    )

    /**
     * Representative color used for the swatch shown in the theme picker.
     */
    fun previewColor(themeColor: ThemeColor, darkTheme: Boolean): Color? {
        if (themeColor == ThemeColor.Umihi) {
            return if (darkTheme) Color(0xFFFFB4A8) else Color(0xFFA53F32)
        }
        val spec = specs[themeColor] ?: return null
        val palette = TonalPalette(spec.hue, spec.chroma)
        return if (darkTheme) palette.tone(80) else palette.tone(40)
    }

    fun colorScheme(themeColor: ThemeColor, darkTheme: Boolean): ColorScheme? {
        val spec = specs[themeColor] ?: return null
        return if (darkTheme) darkScheme(spec) else lightScheme(spec)
    }

    /**
     * Builds a full Material 3 color scheme from an arbitrary seed hue/chroma, e.g. one extracted
     * from artwork. Tone pairs guarantee text contrast for every role.
     */
    fun colorScheme(hue: Float, chroma: Float, darkTheme: Boolean): ColorScheme {
        val spec = SeedSpec(hue = hue, chroma = chroma)
        return if (darkTheme) darkScheme(spec) else lightScheme(spec)
    }

    private class TonalPalette(private val hue: Float, private val chroma: Float) {
        fun tone(tone: Int): Color {
            if (tone <= 0) return Color.Black
            if (tone >= 100) return Color.White
            // Slightly reduce saturation at the tonal extremes, mirroring how chroma
            // collapses near black/white in the HCT model.
            val distanceFromMid = kotlin.math.abs(tone - 50) / 50f
            val saturation = (chroma * (1f - 0.35f * distanceFromMid)).coerceIn(0f, 1f)
            return Color.hsl(hue, saturation, tone / 100f)
        }
    }

    private class Palettes(spec: SeedSpec) {
        val primary = TonalPalette(spec.hue, spec.chroma)
        val secondary = TonalPalette(spec.hue, spec.chroma * 0.45f)
        val tertiary = TonalPalette((spec.hue + 60f) % 360f, spec.chroma * 0.55f)
        val neutral = TonalPalette(spec.hue, spec.chroma * 0.08f)
        val neutralVariant = TonalPalette(spec.hue, spec.chroma * 0.16f)
    }

    private fun lightScheme(spec: SeedSpec): ColorScheme {
        val p = Palettes(spec)
        return lightColorScheme(
            primary = p.primary.tone(40),
            onPrimary = p.primary.tone(100),
            primaryContainer = p.primary.tone(90),
            onPrimaryContainer = p.primary.tone(10),
            secondary = p.secondary.tone(40),
            onSecondary = p.secondary.tone(100),
            secondaryContainer = p.secondary.tone(90),
            onSecondaryContainer = p.secondary.tone(10),
            tertiary = p.tertiary.tone(40),
            onTertiary = p.tertiary.tone(100),
            tertiaryContainer = p.tertiary.tone(90),
            onTertiaryContainer = p.tertiary.tone(10),
            error = Color(0xFFBA1A1A),
            onError = Color(0xFFFFFFFF),
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF93000A),
            background = p.neutral.tone(98),
            onBackground = p.neutral.tone(10),
            surface = p.neutral.tone(98),
            onSurface = p.neutral.tone(10),
            surfaceVariant = p.neutralVariant.tone(90),
            onSurfaceVariant = p.neutralVariant.tone(30),
            outline = p.neutralVariant.tone(50),
            outlineVariant = p.neutralVariant.tone(80),
            scrim = Color.Black,
            inverseSurface = p.neutral.tone(20),
            inverseOnSurface = p.neutral.tone(95),
            inversePrimary = p.primary.tone(80),
            surfaceDim = p.neutral.tone(87),
            surfaceBright = p.neutral.tone(98),
            surfaceContainerLowest = p.neutral.tone(100),
            surfaceContainerLow = p.neutral.tone(96),
            surfaceContainer = p.neutral.tone(94),
            surfaceContainerHigh = p.neutral.tone(92),
            surfaceContainerHighest = p.neutral.tone(90),
        )
    }

    private fun darkScheme(spec: SeedSpec): ColorScheme {
        val p = Palettes(spec)
        return darkColorScheme(
            primary = p.primary.tone(80),
            onPrimary = p.primary.tone(20),
            primaryContainer = p.primary.tone(30),
            onPrimaryContainer = p.primary.tone(90),
            secondary = p.secondary.tone(80),
            onSecondary = p.secondary.tone(20),
            secondaryContainer = p.secondary.tone(30),
            onSecondaryContainer = p.secondary.tone(90),
            tertiary = p.tertiary.tone(80),
            onTertiary = p.tertiary.tone(20),
            tertiaryContainer = p.tertiary.tone(30),
            onTertiaryContainer = p.tertiary.tone(90),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
            background = p.neutral.tone(6),
            onBackground = p.neutral.tone(90),
            surface = p.neutral.tone(6),
            onSurface = p.neutral.tone(90),
            surfaceVariant = p.neutralVariant.tone(30),
            onSurfaceVariant = p.neutralVariant.tone(80),
            outline = p.neutralVariant.tone(60),
            outlineVariant = p.neutralVariant.tone(30),
            scrim = Color.Black,
            inverseSurface = p.neutral.tone(90),
            inverseOnSurface = p.neutral.tone(20),
            inversePrimary = p.primary.tone(40),
            surfaceDim = p.neutral.tone(6),
            surfaceBright = p.neutral.tone(24),
            surfaceContainerLowest = p.neutral.tone(4),
            surfaceContainerLow = p.neutral.tone(10),
            surfaceContainer = p.neutral.tone(12),
            surfaceContainerHigh = p.neutral.tone(17),
            surfaceContainerHighest = p.neutral.tone(22),
        )
    }
}
