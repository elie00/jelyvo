package ca.ilianokokoro.umihi.music.ui.screens.player.components

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ca.ilianokokoro.umihi.music.core.lyrics.LyricsTranslation.Result
import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.models.LyricLine
import ca.ilianokokoro.umihi.music.models.Lyrics
import ca.ilianokokoro.umihi.music.ui.theme.StudioTestTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class LyricsTranslationPanelTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun lyricsOf(text: String) = Lyrics(listOf(LyricLine(0, text)), isSynced = true)

    private fun show(lyrics: Lyrics, translate: suspend (Lyrics, String) -> Result) {
        compose.setContent {
            StudioTestTheme(dark = true) {
                LyricsPanel(
                    lyricsState = ApiResult.Success(lyrics),
                    currentPositionMs = 0,
                    isPlaying = false,
                    translate = translate,
                )
            }
        }
        compose.waitForIdle()
    }

    private fun tapTranslate() {
        compose.onNodeWithContentDescription("Translate lyrics").performClick()
        compose.waitForIdle()
    }

    @Test
    fun `the toggle swaps the lines, and comes back without asking twice`() {
        var requests = 0
        show(lyricsOf("Hello")) { _, _ ->
            requests++
            Result.Translated(lyricsOf("Bonjour"), "en")
        }
        compose.onNodeWithText("Hello").assertIsDisplayed()

        tapTranslate()
        compose.onNodeWithText("Bonjour").assertIsDisplayed()

        compose.onNodeWithContentDescription("Show original lyrics").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Hello").assertIsDisplayed()

        tapTranslate()
        compose.onNodeWithText("Bonjour").assertIsDisplayed()
        assertEquals(1, requests)
    }

    @Test
    fun `nothing is requested until someone asks`() {
        var requests = 0
        show(lyricsOf("Hello")) { _, _ ->
            requests++
            Result.Unavailable
        }
        assertEquals(0, requests)
    }

    @Test
    fun `a failure leaves the original on screen, and the next tap tries again`() {
        var requests = 0
        show(lyricsOf("Hello")) { _, _ ->
            requests++
            Result.Unavailable
        }
        tapTranslate()
        compose.onNodeWithText("Hello").assertIsDisplayed()
        tapTranslate()
        assertEquals(2, requests)
    }

    @Test
    fun `lyrics already in the language stay as they are`() {
        show(lyricsOf("Hello")) { _, _ -> Result.SameLanguage("en") }
        tapTranslate()
        compose.onNodeWithText("Hello").assertIsDisplayed()
        compose.onNodeWithContentDescription("Translate lyrics").assertIsDisplayed()
    }

    @Test
    fun `changing song drops the translation of the last one`() {
        var lyrics by mutableStateOf(lyricsOf("Hello"))
        compose.setContent {
            StudioTestTheme(dark = true) {
                LyricsPanel(
                    lyricsState = ApiResult.Success(lyrics),
                    currentPositionMs = 0,
                    isPlaying = false,
                    translate = { _, _ -> Result.Translated(lyricsOf("Bonjour"), "en") },
                )
            }
        }
        tapTranslate()
        compose.onNodeWithText("Bonjour").assertIsDisplayed()

        lyrics = lyricsOf("World")
        compose.waitForIdle()
        compose.onNodeWithText("World").assertIsDisplayed()
        compose.onNodeWithText("Bonjour").assertDoesNotExist()
    }

    @Test
    fun `a sleep timer takes any minutes a day can hold`() {
        assertEquals(7, parseSleepMinutes("7"))
        assertEquals(1440, parseSleepMinutes("1440"))
        assertNull(parseSleepMinutes("0"))
        assertNull(parseSleepMinutes("1441"))
        assertNull(parseSleepMinutes(""))
    }

}
