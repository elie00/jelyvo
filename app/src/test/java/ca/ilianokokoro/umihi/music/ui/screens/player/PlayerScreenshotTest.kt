package ca.ilianokokoro.umihi.music.ui.screens.player

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.ThemePreset
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.ui.screens.player.components.PlayerControls
import ca.ilianokokoro.umihi.music.ui.theme.UmihiMusicTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the player's portrait layout to `app/build/outputs/theme-showcase`, to be looked at. It
 * lays out the same pieces with the same weights as [PlayerScreen], without its ViewModel. The
 * spacers stand in for the status bar, the top bar and three-button navigation, so the small-screen
 * image shows whether everything really fits.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class PlayerScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun render(name: String, dark: Boolean, isPlaying: Boolean = true) {
        compose.setContent {
            UmihiMusicTheme(darkTheme = dark, themePreset = ThemePreset.Studio) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    Spacer(Modifier.height(80.dp))
                    Thumbnail(
                        href = "",
                        isPlaying = isPlaying,
                        modifier = Modifier.fillMaxWidth().weight(1.08f),
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.92f)
                            .padding(horizontal = 30.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SongInfo(
                            song = Song(
                                youtubeId = "x",
                                title = "Harder, Better, Faster, Stronger",
                                artist = "Daft Punk",
                            ),
                            isLoggedIn = true,
                            isLiked = true,
                            showMoreButton = false,
                        )
                        PlayerControls(
                            isPlaying = isPlaying,
                            isLoading = false,
                            progress = PlaybackProgress(position = 84_000f, duration = 224_000f),
                            onSeekPlayer = {},
                            onUpdateSeekBarHeldState = {},
                            onSeek = {},
                            onOpenQueue = {},
                            onOpenSleepTimer = {},
                            onOpenSpeedSelector = {},
                            playbackSpeed = 1f,
                            sleepTimerRemainingSeconds = null,
                        )
                    }
                    Spacer(Modifier.height(48.dp))
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
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun playerDark() = render("player-dark", dark = true)

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun playerPaused() = render("player-paused-light", dark = false, isPlaying = false)

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun playerSmallScreen() = render("player-small", dark = true)
}
