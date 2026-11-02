package ca.ilianokokoro.umihi.music.ui.screens.player.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.ThemePreset
import ca.ilianokokoro.umihi.music.models.LyricLine
import ca.ilianokokoro.umihi.music.models.LyricWord
import ca.ilianokokoro.umihi.music.models.Lyrics
import ca.ilianokokoro.umihi.music.ui.theme.UmihiMusicTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the lyrics panel to `app/build/outputs/theme-showcase`, to be looked at. The panel is
 * paused here: playing, its clock asks for a frame forever and the test would never go idle.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class LyricsPanelScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun wordTimed(startMs: Long, text: String): LyricLine {
        var clock = startMs
        val words = text.split(" ").map { word ->
            LyricWord(clock, clock + 400L, word).also { clock += 400L }
        }
        return LyricLine(timeMs = startMs, text = text, words = words)
    }

    private fun render(name: String, dark: Boolean, lyrics: Lyrics, positionMs: Long) {
        compose.setContent {
            UmihiMusicTheme(darkTheme = dark, themePreset = ThemePreset.Studio) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(891.dp)
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    LyricsPanel(
                        lyricsState = ApiResult.Success(lyrics),
                        currentPositionMs = positionMs,
                        isPlaying = false,
                        modifier = Modifier.height(460.dp),
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

    private val timedLyrics = Lyrics(
        lines = listOf(
            wordTimed(0L, "Work it, make it"),
            wordTimed(4_000L, "Do it, makes us"),
            // Long enough to wrap, so the sweep is seen crossing from one visual line to the next.
            wordTimed(8_000L, "Harder better faster stronger, more than ever hour after hour"),
            LyricLine(timeMs = 14_000L, text = ""),
            wordTimed(16_000L, "Work is never over"),
            wordTimed(20_000L, "Do it faster, makes us stronger"),
        ),
        isSynced = true,
    )

    @Test
    fun sweepDark() = render("lyrics-sweep-dark", dark = true, timedLyrics, positionMs = 9_450L)

    @Test
    fun sweepLight() = render("lyrics-sweep-light", dark = false, timedLyrics, positionMs = 9_450L)

    @Test
    fun lineTimedDark() = render(
        name = "lyrics-lines-dark",
        dark = true,
        lyrics = Lyrics(
            lines = listOf(
                LyricLine(0L, "A line timed on its own"),
                LyricLine(4_000L, "Is lit whole, with no made-up sweep"),
                LyricLine(8_000L, "And the next one waits"),
            ),
            isSynced = true,
        ),
        positionMs = 5_000L,
    )
}
