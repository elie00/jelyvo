package ca.ilianokokoro.umihi.music.ui.components.studio

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import ca.ilianokokoro.umihi.music.ui.theme.StudioTestTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the menu's slab and the buttons to `app/build/outputs/theme-showcase`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class StudioMenuScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = compose.runOnIdle {
            val view = compose.activity.window.decorView
            createBitmap(view.width, view.height).also { view.draw(Canvas(it)) }
        }
        val directory = File("build/outputs/theme-showcase").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun render(name: String, dark: Boolean) {
        compose.setContent {
            StudioTestTheme(dark = dark) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // The slab as Material's popup draws it: a window of its own cannot be captured.
                        val colors = studioMenuColors()
                        Column(
                            Modifier
                                .align(Alignment.End)
                                .width(StudioMenuWidth)
                                .shadow(StudioMenuShadow, StudioMenuShape)
                                .clip(StudioMenuShape)
                                .background(colors.fill)
                                .border(0.5.dp, colors.rim, StudioMenuShape)
                                .padding(vertical = 8.dp),
                        ) {
                            StudioMenuItem("Lancer une radio", {}, Icons.Rounded.Radio)
                            StudioMenuItem("Trier par titre", {}, Icons.Rounded.Check, value = "A-Z")
                            StudioMenuItem("Sans icône", {}, null, description = "Une seconde ligne")
                            StudioMenuSeparator()
                            StudioMenuItem("Retirer de l'historique d'écoute", {}, Icons.Rounded.Delete)
                            StudioMenuItem("Indisponible", {}, Icons.Rounded.Close, enabled = false)
                        }

                        Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Analyser le lien") }
                        Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("Désactivé") }
                        FilledTonalButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                            Icon(
                                Icons.Rounded.Search,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                            Text(
                                "Explorer sans compte",
                                modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                            )
                        }
                        OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Modifier le lien") }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FilledTonalButton(
                                onClick = {},
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                ),
                            ) { Text("Réessayer") }
                            FilledIconButton(onClick = {}) {
                                Icon(Icons.Rounded.Search, contentDescription = null)
                            }
                            CircleButton(onClick = {}, size = 56.dp, filled = true) {
                                Icon(Icons.Rounded.Pause, contentDescription = null)
                            }
                            ActionPill(
                                icon = Icons.Rounded.PlayArrow,
                                label = "Importer",
                                onClick = {},
                                filled = false,
                                size = 44.dp,
                                horizontalPadding = 18.dp,
                            )
                        }
                    }
                }
            }
        }
        capture(name)
    }

    @Test
    fun menuAndButtonsDark() = render("menu-buttons-dark", dark = true)

    @Test
    fun menuAndButtonsLight() = render("menu-buttons-light", dark = false)

    @Test
    fun `the real menu opens from its anchor, answers a tap and closes`() {
        var expanded by mutableStateOf(true)
        var tapped = 0
        compose.setContent {
            StudioTestTheme(dark = true) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
                    Box(Modifier.size(48.dp)) {
                        StudioMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            StudioMenuItem(
                                text = "Retirer",
                                leadingIcon = Icons.Rounded.Delete,
                                onClick = {
                                    tapped++
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Retirer").performClick()
        compose.waitForIdle()
        assertEquals(1, tapped)
        compose.onNodeWithText("Retirer").assertDoesNotExist()
    }

    @Test
    fun `a disabled menu row ignores the tap`() {
        var tapped = 0
        compose.setContent {
            StudioTestTheme(dark = true) {
                Box(Modifier.size(48.dp)) {
                    StudioMenu(expanded = true, onDismissRequest = {}) {
                        StudioMenuItem("Indisponible", { tapped++ }, Icons.Rounded.Close, enabled = false)
                    }
                }
            }
        }
        compose.onNodeWithText("Indisponible").performClick()
        compose.waitForIdle()
        assertEquals(0, tapped)
    }

    @Test
    fun `a button answers a tap, and a disabled one does not`() {
        var tapped = 0
        compose.setContent {
            StudioTestTheme(dark = true) {
                Column {
                    Button(onClick = { tapped++ }) { Text("Actif") }
                    Button(onClick = { tapped += 10 }, enabled = false) { Text("Inactif") }
                }
            }
        }
        compose.onNodeWithText("Actif").performClick()
        compose.onNodeWithText("Inactif").performClick()
        compose.waitForIdle()
        assertEquals(1, tapped)
    }
}
