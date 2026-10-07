package ca.ilianokokoro.umihi.music.desktop.library

import ca.ilianokokoro.umihi.music.core.domain.Track
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class DesktopLibraryTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun `favorites history and duplicate queue slots survive restart`() = runBlocking {
        val path = temp.root.toPath()
        val store = DesktopLibrary(path)
        store.restore()
        store.toggleFavorite(Track("a"))
        store.recordPlay(Track("a"))
        store.recordPlay(Track("b"))
        store.recordPlay(Track("a"))
        store.savePlaylist(" Test ", listOf(Track("a"), Track("a")))
        val restored = DesktopLibrary(path)
        restored.restore()
        assertEquals(listOf("a"), restored.state.value.favorites.map { it.id })
        assertEquals(listOf("a", "b"), restored.state.value.recents.map { it.id })
        assertEquals(2, restored.state.value.playlists.single().tracks.size)
        assertEquals("Test", restored.state.value.playlists.single().name)
    }

    @Test fun `concurrent writes retain all favorites`() = runBlocking {
        val store = DesktopLibrary(temp.root.toPath())
        store.restore()
        coroutineScope { repeat(25) { launch { store.toggleFavorite(Track("$it")) } } }
        assertEquals(25, store.state.value.favorites.size)
    }

    @Test fun `oversized update cannot create a library that fails on restart`() = runBlocking {
        val path = temp.root.toPath()
        val store = DesktopLibrary(path)
        store.restore()
        store.toggleFavorite(Track("a"))
        assertTrue(runCatching { store.toggleFavorite(Track("large", title = "é".repeat(4 * 1024 * 1024))) }.isFailure)
        val restored = DesktopLibrary(path)
        restored.restore()
        assertEquals(listOf("a"), restored.state.value.favorites.map { it.id })
    }

    @Test fun `corrupt or future library is preserved on failure`() = runBlocking {
        for (content in listOf("broken", "{\"version\":2}")) {
            val file = temp.root.toPath().resolve("library.json")
            Files.writeString(file, content)
            val store = DesktopLibrary(temp.root.toPath())
            assertTrue(runCatching { store.restore() }.isFailure)
            assertTrue(runCatching { store.toggleFavorite(Track("a")) }.isFailure)
            assertEquals(content, Files.readString(file))
        }
    }
}
