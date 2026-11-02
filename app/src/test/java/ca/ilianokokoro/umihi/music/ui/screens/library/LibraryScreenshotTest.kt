package ca.ilianokokoro.umihi.music.ui.screens.library

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import ca.ilianokokoro.umihi.music.models.PlaylistInfo
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.ui.components.FrostedPageHeader
import ca.ilianokokoro.umihi.music.ui.components.PageGutter
import ca.ilianokokoro.umihi.music.ui.components.PillFilterChip
import ca.ilianokokoro.umihi.music.ui.components.bleedToPageEdges
import ca.ilianokokoro.umihi.music.ui.components.playlist.PlaylistCard
import ca.ilianokokoro.umihi.music.ui.components.song.SongListItem
import ca.ilianokokoro.umihi.music.ui.theme.StudioTestTheme
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
 * Renders the library's pieces to `app/build/outputs/theme-showcase`, to be looked at. The real
 * screen needs a ViewModel, so this assembles its header, its grid and its rows the same way.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class LibraryScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun render(name: String, dark: Boolean) {
        compose.setContent {
            StudioTestTheme(dark = dark) {
                val haze = remember { HazeState() }
                var headerPx by remember { mutableIntStateOf(0) }
                val header = with(LocalDensity.current) { headerPx.toDp() }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 150.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .hazeSource(haze),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(
                            top = header + 8.dp,
                            start = PageGutter,
                            end = PageGutter,
                        ),
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                "Playlists",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                        items(4) { index ->
                            PlaylistCard(
                                playlistInfo = PlaylistInfo(id = "p$index", title = "Playlist $index"),
                                onClicked = {},
                            )
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                "Titres aimés",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                        items(2, span = { GridItemSpan(maxLineSpan) }) { index ->
                            Box(Modifier.bleedToPageEdges()) {
                                SongListItem(
                                    song = Song(
                                        youtubeId = "song-$index",
                                        title = "Titre aimé $index",
                                        artist = "Daft Punk",
                                        duration = "3:4$index",
                                    ),
                                    onPress = {},
                                    playNext = {},
                                    addToQueue = {},
                                    startRadio = {},
                                )
                            }
                        }
                    }
                    FrostedPageHeader(
                        title = "Bibliothèque",
                        hazeState = haze,
                        onHeightChanged = { headerPx = it },
                        modifier = Modifier.align(Alignment.TopCenter),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = PageGutter),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf("Playlists", "Titres aimés", "Albums", "Artistes")
                                .forEachIndexed { index, label ->
                                    PillFilterChip(
                                        selected = index == 0,
                                        onClick = {},
                                        label = label,
                                    )
                                }
                        }
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
    fun libraryDark() = render("library-dark", dark = true)

    @Test
    fun libraryLight() = render("library-light", dark = false)
}
