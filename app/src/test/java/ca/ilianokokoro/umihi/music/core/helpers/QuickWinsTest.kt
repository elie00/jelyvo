package ca.ilianokokoro.umihi.music.core.helpers

import android.app.Application
import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

class DisplayRefreshTest {
    private val fhd60 = DisplayModeChoice(1, 1080, 2340, 60f)
    private val fhd120 = DisplayModeChoice(2, 1080, 2340, 120f)
    private val qhd60 = DisplayModeChoice(3, 1440, 3120, 60f)
    private val qhd144 = DisplayModeChoice(4, 1440, 3120, 144f)

    @Test
    fun `takes the fastest mode at the current resolution`() {
        assertEquals(2, DisplayRefresh.fastestMode(listOf(fhd60, fhd120, qhd60, qhd144), fhd60))
    }

    @Test
    fun `never changes resolution to go faster`() {
        assertEquals(1, DisplayRefresh.fastestMode(listOf(fhd60, qhd144), fhd60))
    }

    @Test
    fun `with nothing matching it leaves the choice to the system`() {
        assertEquals(DisplayRefresh.NO_PREFERENCE, DisplayRefresh.fastestMode(listOf(qhd60), fhd60))
    }
}

class ExternalAudioTest {
    @Test
    fun `a file name makes a title`() {
        assertEquals("Ma chanson", ExternalAudio.titleFromFileName("Ma chanson.mp3"))
        assertEquals("a.b", ExternalAudio.titleFromFileName("a.b.flac"))
        assertEquals("sans extension", ExternalAudio.titleFromFileName("sans extension"))
    }

    @Test
    fun `no usable name means no title`() {
        assertEquals("", ExternalAudio.titleFromFileName(null))
        assertEquals("", ExternalAudio.titleFromFileName("  "))
        assertEquals("", ExternalAudio.titleFromFileName(".mp3"))
    }

    @Test
    fun `only audio is opened`() {
        assertTrue(ExternalAudio.isAudio("audio/mpeg"))
        assertFalse(ExternalAudio.isAudio("video/mp4"))
        assertFalse(ExternalAudio.isAudio(null))
    }
}

class LyricsCardFitTest {
    @Test
    fun `short text stays at the largest size`() {
        assertEquals(90f, LyricsCard.fitTextSize(40f, 90f, 500) { 100 }, 0f)
    }

    @Test
    fun `long text shrinks until it fits`() {
        val size = LyricsCard.fitTextSize(40f, 90f, 500) { it.toInt() * 10 }
        assertTrue(size * 10 <= 500)
        assertTrue((size + 4) * 10 > 500)
    }

    @Test
    fun `text that never fits stops at the smallest size`() {
        assertEquals(40f, LyricsCard.fitTextSize(40f, 90f, 10) { 1_000 }, 0f)
    }
}

/** Renders cards to `app/build/outputs/theme-showcase`, to be looked at. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class LyricsCardRenderTest {
    private fun save(name: String, bitmap: Bitmap) {
        val directory = File("build/outputs/theme-showcase").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun `a short passage`() {
        val artwork = createBitmap(300, 200).apply { eraseColor(android.graphics.Color.rgb(180, 60, 70)) }
        val card = LyricsCard.render(
            passage = "Une ligne de paroles\nqui reste en tête",
            title = "Un titre",
            artist = "Une artiste",
            artwork = artwork,
        )
        assertEquals(LyricsCard.WIDTH, card.width)
        assertEquals(LyricsCard.HEIGHT, card.height)
        save("lyrics-card-short", card)
    }

    @Test
    fun `a passage too long to fit, with no artwork and a long title`() {
        val card = LyricsCard.render(
            passage = List(80) { "une ligne de paroles qui ne s'arrête pas" }.joinToString("\n"),
            title = "Un titre vraiment beaucoup trop long pour tenir sur une seule ligne de la carte",
            artist = "Une artiste au nom également très long, avec plusieurs invités",
            artwork = null,
        )
        assertEquals(LyricsCard.HEIGHT, card.height)
        save("lyrics-card-long", card)
    }
}
