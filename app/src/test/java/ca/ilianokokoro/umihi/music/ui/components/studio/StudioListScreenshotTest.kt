package ca.ilianokokoro.umihi.music.ui.components.studio

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.core.graphics.createBitmap
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.ui.components.song.SongListItem
import ca.ilianokokoro.umihi.music.ui.theme.StudioTestTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the rows to `app/build/outputs/theme-showcase`, and checks what a finger does on them. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class StudioListScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val longTitle = "Un titre beaucoup trop long pour tenir sur une seule rangée de la liste"

    private fun song(index: Int, title: String = "Titre $index", video: Boolean = false, explicit: Boolean = false) = Song(
        youtubeId = "s$index",
        title = title,
        artist = "Daft Punk",
        duration = "3:4$index",
        isExplicit = explicit,
    ).apply { isVideo = video }

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

    private fun render(name: String, dark: Boolean, page: Color? = null) {
        compose.setContent {
            StudioTestTheme(dark = dark) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .background(page ?: MaterialTheme.colorScheme.background),
                    ) {
                        Row(song(0))
                        Row(song(1, title = longTitle, explicit = true))
                        Row(song(2, video = true))
                        Row(song(3), trackNumber = 3)
                        Row(song(4), trackNumber = 124)
                        Row(song(5), rank = 1)
                        Row(song(6), isDownloaded = true)
                        Row(song(7), isSelected = true, selectionActive = true)
                        ListItem(
                            headlineContent = { Text("Dernière recherche") },
                            leadingContent = { Icon(Icons.Rounded.Close, null) },
                            modifier = Modifier.clickable {},
                        )
                        ListItem(
                            headlineContent = { Text("Justice") },
                            supportingContent = { Text("Artiste") },
                            trailingContent = { Text("12 écoutes") },
                        )
                    }
                }
            }
        }
        capture(name)
    }

    @androidx.compose.runtime.Composable
    private fun Row(
        song: Song,
        trackNumber: Int? = null,
        rank: Int? = null,
        isDownloaded: Boolean? = null,
        isSelected: Boolean = false,
        selectionActive: Boolean = false,
        onPress: () -> Unit = {},
        addToQueue: () -> Unit = {},
    ) {
        SongListItem(
            song = song,
            onPress = onPress,
            playNext = {},
            addToQueue = addToQueue,
            startRadio = {},
            trackNumber = trackNumber,
            rank = rank,
            isDownloaded = isDownloaded,
            isSelected = isSelected,
            selectionActive = selectionActive,
        )
    }

    @Test
    fun rowsDark() = render("rows-dark", dark = true)

    @Test
    fun rowsLight() = render("rows-light", dark = false)

    @Test
    fun rowsOnATintedPage() = render("rows-tinted", dark = true, page = Color(0xFF173F75))

    @Test
    fun `a long title leaves the duration and the menu on the row`() {
        compose.setContent {
            StudioTestTheme(dark = true) {
                Row(song(1, title = longTitle, explicit = true))
            }
        }
        compose.onNodeWithText("3:41").assertIsDisplayed()
        compose.onNodeWithContentDescription("more").assertIsDisplayed()
    }

    @Test
    fun `a tap plays the row and the menu button does not`() {
        var played = 0
        compose.setContent {
            StudioTestTheme(dark = true) {
                Row(song(0), onPress = { played++ })
            }
        }
        compose.onNodeWithContentDescription("more").performClick()
        compose.waitForIdle()
        assertEquals(0, played)
        compose.onNodeWithText("Play next").assertIsDisplayed()
    }

    @Test
    fun `a tap on the row plays it`() {
        var played = 0
        compose.setContent {
            StudioTestTheme(dark = true) {
                Row(song(0), onPress = { played++ })
            }
        }
        compose.onNodeWithText("Titre 0").performClick()
        compose.waitForIdle()
        assertEquals(1, played)
    }

    @Test
    fun `swiping a row to the left queues it and it springs back`() {
        var queued = 0
        compose.setContent {
            StudioTestTheme(dark = true) {
                Box(Modifier.size(androidx.compose.ui.unit.DpSize(411.dp, 80.dp))) {
                    Row(song(0), addToQueue = { queued++ })
                }
            }
        }
        compose.onNodeWithText("Titre 0").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(1, queued)
        compose.onNodeWithText("Titre 0").assertIsDisplayed()
    }

    private val Int.dp get() = androidx.compose.ui.unit.Dp(toFloat())
}
