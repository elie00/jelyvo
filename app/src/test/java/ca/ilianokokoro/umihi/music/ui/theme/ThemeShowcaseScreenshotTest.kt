package ca.ilianokokoro.umihi.music.ui.theme

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.graphics.createBitmap
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders a sample of the theme to `app/build/outputs/theme-showcase`, since a theme's look cannot
 * be checked by assertions alone and no device is needed for this. The images are not reference
 * images: nothing compares against them, they are there to be looked at.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ThemeShowcaseScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun render(name: String, preset: ThemePreset, dark: Boolean) {
        compose.setContent {
            UmihiMusicTheme(
                darkTheme = dark,
                themePreset = preset,
                fontFamily = nunitoFontFamily(),
                studioFontFamily = dmSansFontFamily(),
            ) {
                Showcase()
            }
        }
        compose.waitForIdle()
        // Drawn by hand: captureToImage waits for a hardware redraw that never comes here.
        val bitmap = compose.runOnIdle {
            val view = compose.activity.window.decorView
            createBitmap(view.width, view.height).also { view.draw(Canvas(it)) }
        }
        val directory = File("build/outputs/theme-showcase").apply { mkdirs() }
        val file = File(directory, "$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(file.length() > 0L)
    }

    @Test
    fun studioDark() = render("studio-dark", ThemePreset.Studio, dark = true)

    @Test
    fun studioLight() = render("studio-light", ThemePreset.Studio, dark = false)

    @Test
    fun jelyvoDark() = render("jelyvo-dark", ThemePreset.Jelyvo, dark = true)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Showcase() {
    val type = MaterialTheme.typography
    val colors = MaterialTheme.colorScheme
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(16.dp),
    ) {
        Text("Bibliothèque", style = type.displayLarge, color = colors.onBackground)
        Text("Écoutés récemment", style = type.headlineMedium, color = colors.onBackground)
        Text("Titre de section", style = type.titleLarge, color = colors.onBackground)
        Text("Titre mis en avant", style = type.titleLargeEmphasized, color = colors.primary)
        Text("One More Time", style = type.titleMedium, color = colors.onBackground)
        Text("Daft Punk · Discovery · 2001", style = type.bodyMedium, color = colors.onSurfaceVariant)
        Text("EN LECTURE · FLAC 24 BITS", style = type.labelSmall, color = colors.onSurfaceVariant)
        Text(
            "Le corps de texte reste lisible sur plusieurs lignes, avec ses accents : é è ê à ç ù.",
            style = type.bodyLarge,
            color = colors.onBackground,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(colors.primaryContainer),
                )
                Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                    Text("Harder, Better, Faster", style = type.titleMedium)
                    Text("Daft Punk", style = type.bodyMedium, color = colors.onSurfaceVariant)
                }
                Switch(checked = true, onCheckedChange = {})
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {}) { Text("Lire") }
            FilledTonalButton(onClick = {}) { Text("Aléatoire") }
            OutlinedButton(onClick = {}) { Text("Ajouter") }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(selected = true, onClick = {}, label = { Text("Titres") })
            FilterChip(selected = false, onClick = {}, label = { Text("Albums") })
            TextButton(onClick = {}) { Text("Tout voir") }
        }
        Slider(value = 0.4f, onValueChange = {})
        OutlinedTextField(
            value = "Rechercher",
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
        )
        HorizontalDivider()
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                colors.primary, colors.secondary, colors.tertiary, colors.surfaceVariant,
                colors.surfaceContainer, colors.surfaceContainerHighest, colors.outline, colors.error,
            ).forEach { swatch ->
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .size(40.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(swatch),
                )
            }
        }
    }
}
