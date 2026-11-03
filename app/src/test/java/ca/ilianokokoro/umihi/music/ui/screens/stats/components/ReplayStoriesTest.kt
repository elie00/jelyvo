package ca.ilianokokoro.umihi.music.ui.screens.stats.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.core.graphics.createBitmap
import ca.ilianokokoro.umihi.music.models.ArtistListeningStat
import ca.ilianokokoro.umihi.music.models.TrackListeningStat
import ca.ilianokokoro.umihi.music.ui.screens.stats.WrappedStats
import ca.ilianokokoro.umihi.music.ui.theme.StudioTestTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ReplayStoriesTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val full = WrappedStats(
        year = 2026,
        totalPlays = 1432,
        listeningTimeMs = 312_000_000L,
        topTrack = TrackListeningStat(
            youtubeId = "a",
            title = "Harder, Better, Faster, Stronger",
            artist = "Daft Punk",
            thumbnailHref = "",
            duration = "3:44",
            isExplicit = false,
            playCount = 88,
            lastPlayedAt = 0,
        ),
        topArtist = ArtistListeningStat(artist = "Justice", playCount = 212, lastPlayedAt = 0),
        mostActiveDay = LocalDate.of(2026, 6, 21),
        mostActiveDayPlays = 64,
    )
    private val empty = full.copy(topTrack = null, topArtist = null, mostActiveDay = null)

    @Test
    fun `there is a page for what happened and none for what did not`() {
        assertEquals(5, replayPages(full).size)
        assertEquals(
            listOf(ReplayPage.Intro(2026), ReplayPage.Totals(1432, 312_000_000L)),
            replayPages(empty),
        )
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = compose.runOnIdle {
            val view = compose.activity.window.decorView
            createBitmap(view.width, view.height).also { view.draw(Canvas(it)) }
        }
        save(name, bitmap)
    }

    private fun save(name: String, bitmap: Bitmap) {
        val directory = File("build/outputs/theme-showcase").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun `a tap on the right moves on, a tap on the left goes back, and it stops at the ends`() {
        compose.setContent {
            StudioTestTheme(dark = true) { ReplayContent(full, onClose = {}, onShare = {}) }
        }
        compose.onNodeWithText("Your 2026 in music").assertIsDisplayed()
        capture("replay-intro")

        fun tapRight() = compose.onRoot().performTouchInput { click(center.copy(x = width * 0.8f)) }
        fun tapLeft() = compose.onRoot().performTouchInput { click(center.copy(x = width * 0.1f)) }

        tapRight()
        compose.waitForIdle()
        compose.onNodeWithText("1432 plays").assertIsDisplayed()
        capture("replay-totals")

        tapLeft()
        compose.waitForIdle()
        compose.onNodeWithText("Your 2026 in music").assertIsDisplayed()

        tapLeft()
        compose.waitForIdle()
        compose.onNodeWithText("Your 2026 in music").assertIsDisplayed()

        repeat(6) {
            tapRight()
            compose.waitForIdle()
        }
        compose.onNodeWithText("Most active day").assertIsDisplayed()
    }

    @Test
    fun `close and share answer`() {
        var closed = 0
        var shared = 0
        compose.setContent {
            StudioTestTheme(dark = true) {
                ReplayContent(full, onClose = { closed++ }, onShare = { shared++ })
            }
        }
        compose.onNodeWithContentDescription("Close").performClick()
        compose.onNodeWithText("Share").performClick()
        compose.waitForIdle()
        assertEquals(1, closed)
        assertEquals(1, shared)
    }

    @Test
    fun `the poster is the size stories expect, and something is drawn on it`() {
        val poster = ReplayPoster.render(
            "Votre année 2026 en musique, un titre assez long pour tenir sur deux lignes",
            listOf(
                "Total plays" to "1432 plays",
                "Listening time" to "86h 40min",
                "Top artist" to "Justice",
                "Top track" to "A very long track title that would not fit on a single line of the poster at all",
                "Most active day" to "June 21, 2026",
            ),
        )
        assertEquals(ReplayPoster.WIDTH, poster.width)
        assertEquals(ReplayPoster.HEIGHT, poster.height)
        assertNotEquals(poster.getPixel(0, 0), poster.getPixel(100, 130))
        save("replay-poster", poster)
    }
}
