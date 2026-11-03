package ca.ilianokokoro.umihi.music.ui.screens.library

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.ui.theme.StudioTestTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class LocalSongsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun song(title: String, folder: String?) =
        Song(youtubeId = "local:$title", title = title, artist = "Daft Punk", duration = "3:40").apply {
            isLocal = true
            localFolder = folder
        }

    private val library = listOf(
        song("Aerodynamic", "Music/Albums/Discovery"),
        song("Veridis Quo", "Music/Albums/Discovery"),
        song("Around the World", "Music/Homework"),
        song("Enregistrement", "Download"),
    )

    @Test
    fun `a path names its directory, from either column`() {
        assertEquals("Music/Albums", folderPathOf("Music/Albums/", null))
        assertEquals("/storage/emulated/0/Music", folderPathOf(null, "/storage/emulated/0/Music/a.mp3"))
        assertEquals("Music", folderPathOf("Music/", "/ignored/a.mp3"))
        assertEquals("", folderPathOf(null, null))
        assertEquals("", folderPathOf("", "a.mp3"))
    }

    @Test
    fun `tracks group under their folder's last segment, sorted by name`() {
        val folders = groupByFolder(library)
        assertEquals(listOf("Discovery", "Download", "Homework"), folders.map { it.name })
        assertEquals(listOf("Aerodynamic", "Veridis Quo"), folders[0].songs.map { it.title })
    }

    @Test
    fun `two folders with one name stay two folders`() {
        val folders = groupByFolder(listOf(song("a", "A/Live"), song("b", "B/Live")))
        assertEquals(2, folders.size)
        assertEquals(listOf("A/Live", "B/Live"), folders.map { it.path }.sorted())
    }

    @Test
    fun `tracks without a path stay together rather than vanish`() {
        val folders = groupByFolder(listOf(song("a", null), song("b", null)))
        assertEquals(1, folders.size)
        assertEquals(2, folders.single().songs.size)
    }

    @Test
    fun `a folder that a rescan removed closes instead of showing nothing`() {
        val folders = groupByFolder(library)
        assertEquals("Homework", folderAt(folders, "Music/Homework")?.name)
        assertNull(folderAt(folders, "Music/Gone"))
        assertNull(folderAt(folders, null))
    }

    private fun show() {
        compose.setContent {
            StudioTestTheme(dark = true) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        LocalSongsContent(songs = library, onOpenPlayer = {}, topPadding = 8.dp)
                    }
                }
            }
        }
        compose.waitForIdle()
    }

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

    @Test
    fun `folders open onto their tracks, and the way back returns to the folders`() {
        show()
        compose.onNodeWithText("Aerodynamic").assertIsDisplayed()
        capture("local-songs")

        compose.onNodeWithText("Folders").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Discovery").assertIsDisplayed()
        compose.onNodeWithText("Aerodynamic").assertDoesNotExist()
        capture("local-folders")

        compose.onNodeWithText("Homework").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Around the World").assertIsDisplayed()
        compose.onNodeWithText("Aerodynamic").assertDoesNotExist()
        capture("local-folder-open")

        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.waitForIdle()
        compose.onNodeWithText("Discovery").assertIsDisplayed()
    }

    @Test
    fun `one folder offers no choice of view`() {
        compose.setContent {
            StudioTestTheme(dark = true) {
                LocalSongsContent(
                    songs = listOf(song("a", "Music"), song("b", "Music")),
                    onOpenPlayer = {},
                    topPadding = 8.dp,
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Folders").assertDoesNotExist()
    }
}
