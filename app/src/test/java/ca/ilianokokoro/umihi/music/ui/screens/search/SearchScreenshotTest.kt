package ca.ilianokokoro.umihi.music.ui.screens.search

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.ThemePreset
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.models.SearchItem
import ca.ilianokokoro.umihi.music.ui.components.PageGutter
import ca.ilianokokoro.umihi.music.ui.components.frosted
import ca.ilianokokoro.umihi.music.ui.components.song.SongListItem
import ca.ilianokokoro.umihi.music.ui.screens.search.components.SearchBar
import ca.ilianokokoro.umihi.music.ui.screens.search.components.TopResultCard
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
 * Renders the search header and results to `app/build/outputs/theme-showcase`, to be looked at.
 * The real header is private to [SearchScreen], which needs a ViewModel: this one reproduces it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class SearchScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun song(index: Int) = Song(
        youtubeId = "song-$index",
        title = "Titre de la recherche $index",
        artist = "Daft Punk",
        duration = "3:4$index",
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
                        modifier = Modifier
                            .fillMaxSize()
                            .hazeSource(haze)
                            .padding(top = 152.dp),
                    ) {
                        TopResultCard(
                            item = SearchItem.Track(song(0)),
                            onPlay = {},
                            onOpen = {},
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        repeat(4) { index ->
                            SongListItem(
                                song = song(index + 1),
                                onPress = {},
                                playNext = {},
                                addToQueue = {},
                                startRadio = {},
                            )
                        }
                    }
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .frosted(haze)
                            .padding(top = 24.dp, bottom = 8.dp),
                    ) {
                        SearchBar(
                            modifier = Modifier
                                .padding(horizontal = PageGutter, vertical = 4.dp)
                                .fillMaxWidth(),
                            value = "",
                            onValueChange = {},
                            onSearch = {},
                            onVoiceSearch = {},
                        )
                        Row(
                            modifier = Modifier.padding(horizontal = PageGutter),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf("Tout", "Titres", "Vidéos", "Albums").forEachIndexed { index, label ->
                                FilterChip(
                                    selected = index == 0,
                                    onClick = {},
                                    shape = RoundedCornerShape(percent = 50),
                                    border = null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        labelColor = MaterialTheme.colorScheme.onSurface,
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                                    label = { Text(label) },
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
    fun searchDark() = render("search-dark", dark = true)

    @Test
    fun searchLight() = render("search-light", dark = false)
}
