package ca.ilianokokoro.umihi.music.ui.screens.home

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.ThemePreset
import ca.ilianokokoro.umihi.music.models.HomeFeedItem
import ca.ilianokokoro.umihi.music.models.HomeFeedSection
import ca.ilianokokoro.umihi.music.ui.components.FrostedPageBar
import ca.ilianokokoro.umihi.music.ui.components.FrostedPageBarHeight
import ca.ilianokokoro.umihi.music.ui.components.PageGutter
import ca.ilianokokoro.umihi.music.ui.components.home.HomeFeedSectionRow
import ca.ilianokokoro.umihi.music.ui.theme.UmihiMusicTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the home page's pieces to `app/build/outputs/theme-showcase`, to be looked at. Artwork
 * stays blank here, as nothing loads over the network in this environment.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class HomeScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun album(index: Int) = HomeFeedItem.Album(
        browseId = "album-$index",
        title = "Discovery, édition $index",
        subtitle = "Daft Punk",
        thumbnail = "",
    )

    private fun artist(index: Int) = HomeFeedItem.Artist(
        browseId = "artist-$index",
        name = "Artiste $index",
        thumbnail = "",
    )

    private fun render(name: String, dark: Boolean) {
        compose.setContent {
            UmihiMusicTheme(darkTheme = dark, themePreset = ThemePreset.Studio) {
                val haze = remember { HazeState() }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .hazeSource(haze)
                            .padding(top = FrostedPageBarHeight + 12.dp),
                    ) {
                        Column(Modifier.padding(horizontal = PageGutter)) {
                            Text(
                                "Bon après-midi Élie",
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                "Reprenez là où vous vous étiez arrêté",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HomeFeedSectionRow(
                            section = HomeFeedSection("Nouveautés", List(4, ::album)),
                            onPlay = {},
                            onOpenPlaylist = {},
                            onOpenAlbum = {},
                            onOpenArtist = {},
                        )
                        HomeFeedSectionRow(
                            section = HomeFeedSection("Artistes pour vous", List(4, ::artist)),
                            onPlay = {},
                            onOpenPlaylist = {},
                            onOpenAlbum = {},
                            onOpenArtist = {},
                        )
                    }
                    FrostedPageBar(
                        title = "Jelyvo",
                        hazeState = haze,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
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
    fun homeDark() = render("home-dark", dark = true)

    @Test
    fun homeLight() = render("home-light", dark = false)
}
