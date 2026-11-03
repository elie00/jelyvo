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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import ca.ilianokokoro.umihi.music.ui.components.PlayShuffleRow
import ca.ilianokokoro.umihi.music.ui.components.PlaySplitButtonAction
import ca.ilianokokoro.umihi.music.ui.components.ReleaseActionRow
import ca.ilianokokoro.umihi.music.ui.theme.StudioTestTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the Studio controls to `app/build/outputs/theme-showcase`, to compare with BitChord's. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class StudioComponentsScreenshotTest {
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
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PillSegmentedControl(listOf("Tout", "Titres", "Albums"), 1, {})
                        PillSegmentedControl(
                            listOf("Playlists", "Titres aimés", "Albums", "Artistes", "Sur l'appareil"),
                            3,
                            {},
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Switch(checked = true, onCheckedChange = {})
                            Switch(checked = false, onCheckedChange = {})
                            Switch(checked = true, onCheckedChange = {}, enabled = false)
                        }
                        Slider(value = 0.4f, onValueChange = {}, steps = 11)
                        OutlinedTextField(
                            value = "",
                            onValueChange = {},
                            label = { Text("Code de la partie") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = "ABC123",
                            onValueChange = {},
                            isError = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ReleaseActionRow(
                            onPlay = {},
                            onShuffle = {},
                            menuActions = listOf(
                                PlaySplitButtonAction("Radio", Icons.Rounded.Radio) {},
                                PlaySplitButtonAction("Download", Icons.Rounded.Download) {},
                            ),
                            saved = { CircleIconButton(Icons.Rounded.Star, null, {}) },
                        )
                        PlayShuffleRow(onPlay = {}, onShuffle = {})
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(ARTIST_ACTION_GAP),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircleIconButton(Icons.Rounded.Star, null, {}, size = ARTIST_SIDE_BUTTON)
                            PlayCircle(onClick = {}, description = "Play")
                            CircleIconButton(Icons.Rounded.Star, null, {}, size = ARTIST_SIDE_BUTTON, enabled = false)
                        }
                        AlertCard(
                            title = { Text("Supprimer la playlist ?") },
                            text = { Text("Cette action est définitive et ne peut pas être annulée.") },
                            confirmButton = { TextButton(onClick = {}) { Text("Confirmer") } },
                            dismissButton = { TextButton(onClick = {}) { Text("Annuler") } },
                        )
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
    fun dark() = render("studio-components-dark", dark = true)

    @Test
    fun light() = render("studio-components-light", dark = false)
}
