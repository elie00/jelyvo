package ca.ilianokokoro.umihi.music.ui.theme

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.graphics.createBitmap
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.ui.components.ReleaseActionRow
import ca.ilianokokoro.umihi.music.ui.components.song.SongListItem
import ca.ilianokokoro.umihi.music.ui.components.studio.ARTIST_ACTION_GAP
import ca.ilianokokoro.umihi.music.ui.components.studio.PlayCircle
import ca.ilianokokoro.umihi.music.ui.screens.artist.ArtistHeader
import ca.ilianokokoro.umihi.music.ui.screens.artist.ArtistPhotoBackground
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
class ArtworkPagePaletteTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val blue = PageArtworkSeed(Color(0xFF1E5AA8), Color(0xFF2F8FFF), Color(0xFF173F75))
    private val grey = PageArtworkSeed(Color(0xFF777777), Color(0xFF888888), Color(0xFF666666))

    private fun saturationOf(color: Color): Float =
        FloatArray(3).also { ColorUtils.colorToHSL(color.toArgb(), it) }[1]

    private fun contrast(a: Color, b: Color): Double =
        ColorUtils.calculateContrast(a.toArgb(), b.copy(alpha = 1f).toArgb())

    @Test
    fun `a neutral sleeve stays neutral instead of turning maroon`() {
        for (dark in listOf(true, false)) {
            val palette = grey.toPalette(dark)
            assertEquals(0f, saturationOf(palette.background), 0.02f)
            assertEquals(0f, saturationOf(palette.wash), 0.02f)
        }
    }

    @Test
    fun `saturation is only raised for real colour`() {
        assertEquals(0.05f, adaptedArtworkSaturation(0.05f, 0.2f, 0.6f), 0f)
        assertEquals(0.2f, adaptedArtworkSaturation(0.15f, 0.2f, 0.6f), 0f)
        assertEquals(0.6f, adaptedArtworkSaturation(0.9f, 0.2f, 0.6f), 0f)
    }

    @Test
    fun `dark pages are dark and light pages are light`() {
        assertTrue(blue.toPalette(dark = true).background.luminance() < 0.1f)
        assertTrue(blue.toPalette(dark = false).background.luminance() > 0.6f)
    }

    @Test
    fun `text stays readable on the page, whatever the sleeve`() {
        val seeds = listOf(
            blue,
            grey,
            PageArtworkSeed(Color.Black, Color.Black, Color.Black),
            PageArtworkSeed(Color.White, Color.White, Color.White),
            PageArtworkSeed(Color.Yellow, Color.Yellow, Color.Yellow),
            PageArtworkSeed(Color.Red, Color.Magenta, Color.Green),
            PageArtworkSeed(Color(0xFFFF8C00), Color(0xFFFF8C00), Color(0xFFFF8C00)),
            PageArtworkSeed(Color.Cyan, Color.Cyan, Color.Cyan),
        )
        for (seed in seeds) {
            for (dark in listOf(true, false)) {
                val palette = seed.toPalette(dark)
                for (page in listOf(palette.background, palette.wash)) {
                    assertTrue(contrast(palette.onBackground, page) >= 4.5)
                    assertTrue(contrast(palette.onBackgroundVariant, page) >= 4.5)
                }
            }
        }
    }

    @Test
    fun `reads a sleeve's colours and its bottom edge`() {
        val bitmap = createBitmap(100, 100).apply {
            val canvas = Canvas(this)
            canvas.drawColor(android.graphics.Color.rgb(30, 90, 168))
            val paint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(200, 40, 40) }
            canvas.drawRect(0f, 82f, 100f, 100f, paint)
        }
        val seed = artworkSeedOf(bitmap)
        assertNotNull(seed)
        checkNotNull(seed)
        assertEquals(30 / 255f, seed.dominant.red, 0.08f)
        assertEquals(200 / 255f, seed.edge.red, 0.04f)
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

    @Composable
    private fun Rows() {
        repeat(2) { index ->
            SongListItem(
                song = Song(
                    youtubeId = "s$index",
                    title = "Titre $index",
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

    private fun renderRelease(name: String, dark: Boolean) {
        compose.setContent {
            StudioTestTheme(dark = dark) {
                ArtworkPageTheme(blue.toPalette(dark)) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                                .padding(top = 88.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(300.dp)
                                    .shadow(18.dp, RoundedCornerShape(12.dp))
                                    .background(
                                        Brush.linearGradient(listOf(Color(0xFF2F8FFF), Color(0xFF12305C))),
                                        RoundedCornerShape(12.dp),
                                    )
                            )
                            Text("Random Access Memories", style = MaterialTheme.typography.headlineMedium)
                            Text(
                                "Album • 2013 • 13 titres",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            ReleaseActionRow(
                                onPlay = {},
                                onShuffle = {},
                                menuActions = emptyList(),
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            Rows()
                        }
                    }
                }
            }
        }
        capture(name)
    }

    @Test
    fun releaseDark() = renderRelease("palette-release-dark", dark = true)

    @Test
    fun releaseLight() = renderRelease("palette-release-light", dark = false)

    @Test
    fun `the status bar icons follow the top of the picture, then the page`() {
        val darkPage = 0.02f
        val lightPage = 0.8f
        assertEquals(true, statusBarIconsDark(0.9f, darkPage, photoUnderBar = true))
        assertEquals(false, statusBarIconsDark(0.02f, lightPage, photoUnderBar = true))
        assertEquals(false, statusBarIconsDark(0.9f, darkPage, photoUnderBar = false))
        assertEquals(true, statusBarIconsDark(0.02f, lightPage, photoUnderBar = false))
        // Not read yet: the page answers rather than a guess.
        assertEquals(false, statusBarIconsDark(null, darkPage, photoUnderBar = true))
        assertEquals(true, statusBarIconsDark(null, lightPage, photoUnderBar = true))
    }

    @Test
    fun `reads how bright the top of a picture is, not the rest of it`() {
        fun seedOf(top: Int, rest: Int) = artworkSeedOf(
            createBitmap(100, 100).apply {
                val canvas = Canvas(this)
                canvas.drawColor(rest)
                val paint = android.graphics.Paint().apply { color = top }
                canvas.drawRect(0f, 0f, 100f, 12f, paint)
            }
        )!!
        val brightTop = seedOf(android.graphics.Color.WHITE, android.graphics.Color.BLACK)
        val darkTop = seedOf(android.graphics.Color.BLACK, android.graphics.Color.WHITE)
        assertTrue(brightTop.topLuminance!! > 0.9f)
        assertTrue(darkTop.topLuminance!! < 0.1f)
        assertEquals(brightTop.topLuminance, brightTop.toPalette(dark = true).photoTopLuminance)
    }

    @Test
    fun `the page sets the status bar icons and puts them back when it goes`() {
        var shown by mutableStateOf(true)
        compose.setContent {
            if (shown) StatusBarIcons(dark = true, restoreDark = false)
        }
        compose.waitForIdle()
        val controller = compose.runOnIdle {
            WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView)
        }
        assertEquals(true, controller.isAppearanceLightStatusBars)
        shown = false
        compose.waitForIdle()
        assertEquals(false, controller.isAppearanceLightStatusBars)
    }

    @Test
    fun `a page leaving does not undo the page that replaced it`() {
        var first by mutableStateOf(true)
        var second by mutableStateOf(false)
        compose.setContent {
            if (first) StatusBarIcons(dark = false, restoreDark = false)
            if (second) StatusBarIcons(dark = true, restoreDark = false)
        }
        compose.waitForIdle()
        second = true
        compose.waitForIdle()
        first = false
        compose.waitForIdle()
        val controller = compose.runOnIdle {
            WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView)
        }
        assertEquals(true, controller.isAppearanceLightStatusBars)
    }

    private fun renderArtist(name: String, photo: Bitmap, palette: ArtworkPalette, iconsDark: Boolean) {
        compose.setContent {
            StudioTestTheme(dark = true) {
                ArtworkPageTheme(palette, pageColor = palette.wash) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        val listState = rememberLazyListState()
                        ArtistPhotoBackground(
                            photo = photo,
                            wash = palette.wash,
                            iconsDark = iconsDark,
                            artHeight = 433.dp,
                            listState = listState,
                            modifier = Modifier.matchParentSize(),
                        )
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                            item { ArtistHeader(name = "Daft Punk", artHeight = 433.dp) }
                            item {
                                PlayCircle(
                                    onClick = {},
                                    description = "Play",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = ARTIST_ACTION_GAP)
                                        .height(70.dp),
                                )
                            }
                            item { Rows() }
                        }
                    }
                }
            }
        }
        // The picture loads off the main thread, which waiting for idle does not cover.
        Thread.sleep(1500)
        capture(name)
    }

    @Test
    fun artistBrightPhoto() {
        val photo = createBitmap(400, 420).apply { Canvas(this).drawColor(android.graphics.Color.rgb(245, 240, 230)) }
        val seed = artworkSeedOf(photo)!!
        renderArtist("palette-artist-bright", photo, seed.toPalette(true), iconsDark = true)
    }

    @Test
    fun artistDark() {
        val photo = createBitmap(400, 420).apply {
            val canvas = Canvas(this)
            canvas.drawColor(android.graphics.Color.rgb(200, 120, 70))
            val paint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(90, 30, 50) }
            canvas.drawCircle(200f, 180f, 120f, paint)
        }
        val palette = PageArtworkSeed(Color(0xFFC87846), Color(0xFFE08A50), Color(0xFF8A4A3C)).toPalette(true)
        renderArtist("palette-artist-dark", photo, palette, iconsDark = false)
    }
}
