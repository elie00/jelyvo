package ca.ilianokokoro.umihi.music.ui.components.studio

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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

/** Renders the small parts to `app/build/outputs/theme-showcase`, and checks what a finger does on them. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class StudioIndicatorsScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun render(name: String, dark: Boolean) {
        compose.setContent {
            StudioTestTheme(dark = dark) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        listOf("Automatique" to true, "Haute" to false, "Économie de données" to false).forEach { (label, on) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = on, onClick = null)
                                Text(label, modifier = Modifier.padding(start = 16.dp))
                            }
                        }
                        HorizontalDivider()
                        LinearProgressIndicator(progress = { 0.4f }, modifier = Modifier.fillMaxWidth())
                        LinearProgressIndicator(progress = { 1f }, modifier = Modifier.fillMaxWidth())
                        CircularProgressIndicator(modifier = Modifier.size(22.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = {}) { Icon(Icons.Rounded.Search, null) }
                            IconButton(onClick = {}, enabled = false) { Icon(Icons.Rounded.Search, null) }
                            IconToggleButton(checked = false, onCheckedChange = {}) { Icon(Icons.Rounded.Favorite, null) }
                            IconToggleButton(
                                checked = true,
                                onCheckedChange = {},
                                colors = IconButtonDefaults.iconToggleButtonColors(
                                    checkedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                    checkedContentColor = MaterialTheme.colorScheme.onSurface,
                                ),
                            ) { Icon(Icons.Rounded.Shuffle, null) }
                        }
                    }
                }
            }
        }
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

    @Test
    fun indicatorsDark() = render("indicators-dark", dark = true)

    @Test
    fun indicatorsLight() = render("indicators-light", dark = false)

    @Test
    fun `an icon button answers a tap, and a disabled one does not`() {
        var tapped = 0
        compose.setContent {
            StudioTestTheme(dark = true) {
                Column {
                    IconButton(onClick = { tapped++ }) { Icon(Icons.Rounded.Search, "active") }
                    IconButton(onClick = { tapped += 10 }, enabled = false) { Icon(Icons.Rounded.Search, "inactive") }
                }
            }
        }
        compose.onNodeWithContentDescription("active", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("inactive", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals(1, tapped)
    }

    @Test
    fun `a toggle flips and reports its state`() {
        var on by mutableStateOf(false)
        compose.setContent {
            StudioTestTheme(dark = true) {
                IconToggleButton(checked = on, onCheckedChange = { on = it }) {
                    Icon(Icons.Rounded.Favorite, "like")
                }
            }
        }
        compose.onNodeWithContentDescription("like").assertIsNotSelected()
        compose.onNodeWithContentDescription("like").performClick()
        compose.waitForIdle()
        assertEquals(true, on)
        compose.onNodeWithContentDescription("like").assertIsSelected()
    }
}
