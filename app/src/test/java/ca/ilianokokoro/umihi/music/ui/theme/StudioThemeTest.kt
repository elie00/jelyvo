package ca.ilianokokoro.umihi.music.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioThemeTest {
    private fun scheme(dark: Boolean): ColorScheme =
        ThemePresets.colorScheme(ThemePreset.Studio, dark)!!

    private fun contrast(a: Color, b: Color): Double {
        val high = maxOf(a.luminance(), b.luminance()) + 0.05
        val low = minOf(a.luminance(), b.luminance()) + 0.05
        return high / low.toDouble()
    }

    private fun assertReadable(name: String, text: Color, background: Color, minimum: Double) {
        val ratio = contrast(text, background)
        assertTrue("$name contrast is only $ratio, needs $minimum", ratio >= minimum)
    }

    private fun textPairs(s: ColorScheme) = listOf(
        Triple("onPrimary", s.onPrimary, s.primary),
        Triple("onPrimaryContainer", s.onPrimaryContainer, s.primaryContainer),
        Triple("onSecondary", s.onSecondary, s.secondary),
        Triple("onSecondaryContainer", s.onSecondaryContainer, s.secondaryContainer),
        Triple("onTertiary", s.onTertiary, s.tertiary),
        Triple("onTertiaryContainer", s.onTertiaryContainer, s.tertiaryContainer),
        Triple("onBackground", s.onBackground, s.background),
        Triple("onSurface", s.onSurface, s.surface),
        Triple("onSurface on the highest container", s.onSurface, s.surfaceContainerHighest),
        Triple("onSurfaceVariant on surface", s.onSurfaceVariant, s.surface),
        Triple("onSurfaceVariant on surfaceVariant", s.onSurfaceVariant, s.surfaceVariant),
        Triple("onError", s.onError, s.error),
        Triple("onErrorContainer", s.onErrorContainer, s.errorContainer),
        Triple("inverseOnSurface", s.inverseOnSurface, s.inverseSurface),
    )

    @Test
    fun `every text colour is readable on its background`() {
        listOf(true, false).forEach { dark ->
            textPairs(scheme(dark)).forEach { (name, text, background) ->
                assertReadable("$name (dark=$dark)", text, background, 4.5)
            }
        }
    }

    @Test
    fun `borders stay visible`() {
        listOf(true, false).forEach { dark ->
            val s = scheme(dark)
            assertReadable("outline (dark=$dark)", s.outline, s.surface, 3.0)
        }
    }

    @Test
    fun `the palette carries no colour of its own apart from errors`() {
        listOf(true, false).forEach { dark ->
            val s = scheme(dark)
            listOf(
                s.primary, s.primaryContainer, s.secondary, s.secondaryContainer,
                s.tertiary, s.tertiaryContainer, s.background, s.surface,
                s.surfaceVariant, s.onSurfaceVariant, s.outline, s.outlineVariant,
                s.surfaceContainer, s.surfaceContainerHigh, s.surfaceContainerHighest,
            ).forEach { color ->
                val spread = maxOf(color.red, color.green, color.blue) -
                    minOf(color.red, color.green, color.blue)
                assertTrue("$color (dark=$dark) is tinted", spread < 0.03f)
            }
        }
    }

    @Test
    fun `containers step away from the background in order`() {
        val dark = scheme(true)
        val steps = listOf(
            dark.surfaceContainerLowest, dark.surfaceContainerLow, dark.surfaceContainer,
            dark.surfaceContainerHigh, dark.surfaceContainerHighest,
        ).map { it.luminance() }
        assertEquals(steps.sorted(), steps)

        val light = scheme(false)
        val lightSteps = listOf(
            light.surfaceContainerLowest, light.surfaceContainerLow, light.surfaceContainer,
            light.surfaceContainerHigh, light.surfaceContainerHighest,
        ).map { it.luminance() }
        assertEquals(lightSteps.sortedDescending(), lightSteps)
    }

    @Test
    fun `containers stand out on a card`() {
        listOf(true, false).forEach { dark ->
            val s = scheme(dark)
            listOf(s.primaryContainer, s.secondaryContainer, s.tertiaryContainer).forEach {
                assertTrue(
                    "$it disappears on a card (dark=$dark)",
                    contrast(it, s.surfaceContainerHighest) >= 1.15,
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    private fun allStyles(typography: Typography) = typography.run {
        listOf(
            displayLarge, displayMedium, displaySmall, headlineLarge, headlineMedium,
            headlineSmall, titleLarge, titleMedium, titleSmall, bodyLarge, bodyMedium,
            bodySmall, labelLarge, labelMedium, labelSmall,
            displayLargeEmphasized, displayMediumEmphasized, displaySmallEmphasized,
            headlineLargeEmphasized, headlineMediumEmphasized, headlineSmallEmphasized,
            titleLargeEmphasized, titleMediumEmphasized, titleSmallEmphasized,
            bodyLargeEmphasized, bodyMediumEmphasized, bodySmallEmphasized,
            labelLargeEmphasized, labelMediumEmphasized, labelSmallEmphasized,
        )
    }

    @Test
    fun `every style is set in DM Sans, emphasized ones included`() {
        allStyles(studioTypography(FontFamily.Serif)).forEach {
            assertEquals(FontFamily.Serif, it.fontFamily)
        }
    }

}
