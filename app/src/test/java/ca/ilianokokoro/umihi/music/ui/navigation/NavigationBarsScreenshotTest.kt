package ca.ilianokokoro.umihi.music.ui.navigation

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.ThemePreset
import ca.ilianokokoro.umihi.music.ui.components.frosted
import ca.ilianokokoro.umihi.music.ui.components.frostedTopAppBarColors
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
 * Renders both bars over scrolling content to `app/build/outputs/theme-showcase`, to be looked at.
 * This environment never draws the blur itself, which can only be checked on a device.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class NavigationBarsScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val artwork = listOf(
        Color(0xFFD9483B), Color(0xFF2F6FDE), Color(0xFFE9B949), Color(0xFF3FA796),
        Color(0xFF8E5BD9), Color(0xFFE56AA0),
    )

    private fun render(name: String, dark: Boolean, tab: Any) {
        compose.setContent {
            UmihiMusicTheme(darkTheme = dark, themePreset = ThemePreset.Studio) {
                val pageHaze = remember { HazeState() }
                val dockHaze = remember { HazeState() }
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    Scaffold(
                        modifier = Modifier.hazeSource(dockHaze),
                        topBar = {
                            TopAppBar(
                                modifier = Modifier.frosted(pageHaze),
                                colors = frostedTopAppBarColors(),
                                title = { Text("Discovery") },
                            )
                        },
                    ) { padding ->
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().hazeSource(pageHaze),
                            contentPadding = PaddingValues(top = padding.calculateTopPadding()),
                        ) {
                            items(List(14) { it }) { index ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                ) {
                                    Box(
                                        Modifier
                                            .size(56.dp)
                                            .clip(MaterialTheme.shapes.small)
                                            .background(artwork[index % artwork.size])
                                    )
                                    Column(Modifier.padding(start = 12.dp)) {
                                        Text("Titre ${index + 1}", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "Artiste",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    BottomNavigationBar(
                        currentTab = tab as androidx.navigation3.runtime.NavKey,
                        onTabSelected = {},
                        hazeState = dockHaze,
                        modifier = Modifier.align(Alignment.BottomCenter),
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
    fun barsDark() = render("bars-dark", dark = true, tab = HomeScreenKey)

    @Test
    fun barsLight() = render("bars-light", dark = false, tab = SearchScreenKey)

    // No blur below Android 12: the bars must stay readable on their tint alone.
    @Test
    @Config(sdk = [30])
    fun barsLightWithoutBlur() = render("bars-light-android11", dark = false, tab = SearchScreenKey)
}
