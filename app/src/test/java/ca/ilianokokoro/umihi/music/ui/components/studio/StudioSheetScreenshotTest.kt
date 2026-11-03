package ca.ilianokokoro.umihi.music.ui.components.studio

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import ca.ilianokokoro.umihi.music.core.managers.AbLoop
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.ui.screens.player.components.PlayerMenuContent
import ca.ilianokokoro.umihi.music.ui.screens.player.components.QueuePanel
import ca.ilianokokoro.umihi.music.ui.theme.StudioTestTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the sheets to `app/build/outputs/theme-showcase`, to compare with BitChord's. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class StudioSheetScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun song(index: Int, suggestion: Boolean = false) = Song(
        youtubeId = "s$index",
        title = if (index == 1) "Un titre suffisamment long pour ne pas tenir sur la rangée" else "Titre $index",
        artist = "Daft Punk",
        duration = "3:4$index",
        isExplicit = index == 2,
    ).apply { isSuggestion = suggestion }

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

    private fun renderSheet(name: String, dark: Boolean, title: String? = null, content: @Composable () -> Unit) {
        compose.setContent {
            StudioTestTheme(dark = dark) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        SheetCard(title = title) { content() }
                    }
                }
            }
        }
        capture(name)
    }

    @Composable
    private fun Menu(abLoop: AbLoop? = null) {
        PlayerMenuContent(
            song = song(0),
            abLoop = abLoop,
            onStartRadio = {},
            onAddToPlaylist = {},
            onOpenArtist = {},
            onNotInterested = {},
            onOpenAlbum = {},
            onPlayVideoVersion = {},
            onShowDetails = {},
            onShare = {},
            onToggleAbLoop = {},
            onOpenEqualizer = {},
        )
    }

    @Composable
    private fun Queue() {
        QueuePanel(
            songs = listOf(song(0), song(1), song(2), song(3), song(4, true), song(5, true), song(6, true)),
            currentIndex = 1,
            modifier = Modifier.height(760.dp),
        )
    }

    @Test
    fun `the player menu shows its last row on a phone, without scrolling`() {
        renderSheet("sheet-player-menu-reach", dark = true) { Menu() }
        compose.onNodeWithText("Equalizer").assertIsDisplayed()
    }

    @Test
    fun playerMenuDark() = renderSheet("sheet-player-menu-dark", dark = true) { Menu() }

    @Test
    fun playerMenuLight() = renderSheet("sheet-player-menu-light", dark = false) { Menu() }

    @Test
    fun queueDark() = renderSheet("sheet-queue-dark", dark = true) { Queue() }

    @Test
    fun queueLight() = renderSheet("sheet-queue-light", dark = false) { Queue() }
}
