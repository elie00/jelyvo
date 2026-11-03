package ca.ilianokokoro.umihi.music.ui.components.studio

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.ui.components.song.SongSelectionActionBar
import ca.ilianokokoro.umihi.music.ui.screens.stats.WrappedStats
import ca.ilianokokoro.umihi.music.ui.screens.stats.components.WrappedCard
import ca.ilianokokoro.umihi.music.ui.theme.StudioTestTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the flat panels to `app/build/outputs/theme-showcase`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class StudioCardScreenshotTest {
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
                        WrappedCard(
                            wrapped = WrappedStats(
                                year = 2026,
                                totalPlays = 1432,
                                listeningTimeMs = 312_000_000L,
                                topTrack = null,
                                topArtist = null,
                                mostActiveDay = null,
                                mostActiveDayPlays = 0,
                            ),
                        )
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text("Résumé du profil", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "1 432 événements",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        SongSelectionActionBar(
                            songs = listOf(Song(youtubeId = "a", title = "Titre", artist = "Daft Punk", duration = "3:40")),
                            newPlaylistName = "Sélection",
                            onPlay = {},
                            onAddToQueue = {},
                            onClear = {},
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
    fun cardsDark() = render("cards-dark", dark = true)

    @Test
    fun cardsLight() = render("cards-light", dark = false)
}
