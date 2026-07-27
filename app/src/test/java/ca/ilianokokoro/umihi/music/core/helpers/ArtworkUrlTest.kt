package ca.ilianokokoro.umihi.music.core.helpers

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtworkUrlTest {

    @Test
    fun `upgrades a small w-h thumbnail and keeps the trailing options`() {
        assertEquals(
            "https://lh3.googleusercontent.com/abc=w544-h544-l90-rj",
            ArtworkUrl.upgrade("https://lh3.googleusercontent.com/abc=w120-h120-l90-rj"),
        )
    }

    @Test
    fun `upgrades a small s thumbnail`() {
        assertEquals(
            "https://yt3.ggpht.com/abc=s544-c-k-no-rj",
            ArtworkUrl.upgrade("https://yt3.ggpht.com/abc=s120-c-k-no-rj"),
        )
    }

    @Test
    fun `never downgrades an already large thumbnail`() {
        val large = "https://lh3.googleusercontent.com/abc=w1000-h1000-l90-rj"
        assertEquals(large, ArtworkUrl.upgrade(large))

        val exact = "https://lh3.googleusercontent.com/abc=w544-h544-l90-rj"
        assertEquals(exact, ArtworkUrl.upgrade(exact))
    }

    @Test
    fun `leaves non-resizable hosts untouched`() {
        val ytimg = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"
        assertEquals(ytimg, ArtworkUrl.upgrade(ytimg))

        val local = "content://media/external/audio/albumart/42"
        assertEquals(local, ArtworkUrl.upgrade(local))

        assertEquals("", ArtworkUrl.upgrade(""))
    }

    @Test
    fun `leaves a resizable host without size parameter untouched`() {
        val bare = "https://lh3.googleusercontent.com/abc"
        assertEquals(bare, ArtworkUrl.upgrade(bare))
    }

    @Test
    fun `honors a custom target size`() {
        assertEquals(
            "https://lh3.googleusercontent.com/abc=w226-h226-l90-rj",
            ArtworkUrl.upgrade("https://lh3.googleusercontent.com/abc=w60-h60-l90-rj", 226),
        )
    }
}
