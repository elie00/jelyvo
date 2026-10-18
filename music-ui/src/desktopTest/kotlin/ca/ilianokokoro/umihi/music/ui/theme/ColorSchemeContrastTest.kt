package ca.ilianokokoro.umihi.music.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** WCAG 2 AA: body text needs 4.5:1 against the role it sits on. */
class ColorSchemeContrastTest {

    @Test fun `every seeded accent keeps readable text in both modes`() {
        val failures = seededAccents().flatMap { accent ->
            listOf(false, true).flatMap { dark ->
                contrastFailures("$accent ${mode(dark)}", SeedSchemes.colorScheme(accent, dark)!!)
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun `artwork seeds of any hue and chroma keep readable text`() {
        val failures = (0 until 360 step 5).flatMap { hue ->
            listOf(0f, 0.25f, 0.5f, 0.75f, 1f).flatMap { chroma ->
                listOf(false, true).flatMap { dark ->
                    contrastFailures(
                        "hue $hue chroma $chroma ${mode(dark)}",
                        SeedSchemes.colorScheme(hue.toFloat(), chroma, dark),
                    )
                }
            }
        }
        assertTrue(failures.take(20).joinToString("\n"), failures.isEmpty())
    }

    @Test fun `full presets keep readable text in both modes`() {
        val failures = ThemePreset.entries.flatMap { preset ->
            listOf(false, true).flatMap { dark ->
                ThemePresets.colorScheme(preset, dark)
                    ?.let { contrastFailures("$preset ${mode(dark)}", it) }
                    .orEmpty()
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun `brand and dynamic accents have no seed scheme`() {
        assertNull(SeedSchemes.colorScheme(ThemeColor.Umihi, darkTheme = false))
        assertNull(SeedSchemes.colorScheme(ThemeColor.Dynamic, darkTheme = true))
        assertNull(ThemePresets.colorScheme(ThemePreset.Jelyvo, darkTheme = false))
    }

    @Test fun `picker swatch is the scheme primary`() {
        seededAccents().forEach { accent ->
            listOf(false, true).forEach { dark ->
                assertEquals(
                    "$accent ${mode(dark)}",
                    SeedSchemes.colorScheme(accent, dark)!!.primary,
                    SeedSchemes.previewColor(accent, dark),
                )
            }
        }
        assertNotNull(SeedSchemes.previewColor(ThemeColor.Umihi, darkTheme = false))
    }

    private fun seededAccents() =
        ThemeColor.entries.filterNot { it == ThemeColor.Umihi || it == ThemeColor.Dynamic }

    private fun mode(dark: Boolean) = if (dark) "dark" else "light"

    private fun contrastFailures(label: String, scheme: ColorScheme): List<String> = listOf(
        "primary" to (scheme.primary to scheme.onPrimary),
        "primaryContainer" to (scheme.primaryContainer to scheme.onPrimaryContainer),
        "secondary" to (scheme.secondary to scheme.onSecondary),
        "secondaryContainer" to (scheme.secondaryContainer to scheme.onSecondaryContainer),
        "tertiary" to (scheme.tertiary to scheme.onTertiary),
        "tertiaryContainer" to (scheme.tertiaryContainer to scheme.onTertiaryContainer),
        "error" to (scheme.error to scheme.onError),
        "background" to (scheme.background to scheme.onBackground),
        "surface" to (scheme.surface to scheme.onSurface),
        "surfaceVariant" to (scheme.surfaceVariant to scheme.onSurfaceVariant),
        "inverseSurface" to (scheme.inverseSurface to scheme.inverseOnSurface),
    ).mapNotNull { (role, colors) ->
        val ratio = contrast(colors.first, colors.second)
        if (ratio < MIN_TEXT_CONTRAST) "$label $role ${"%.2f".format(ratio)}:1" else null
    }

    private fun contrast(a: Color, b: Color): Float {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05f) / (dark + 0.05f)
    }

    private companion object {
        const val MIN_TEXT_CONTRAST = 4.5f
    }
}
