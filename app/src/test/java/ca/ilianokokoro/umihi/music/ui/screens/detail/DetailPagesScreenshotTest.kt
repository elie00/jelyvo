package ca.ilianokokoro.umihi.music.ui.screens.detail

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import ca.ilianokokoro.umihi.music.models.Playlist
import ca.ilianokokoro.umihi.music.models.PlaylistInfo
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.ui.components.PageGutter
import ca.ilianokokoro.umihi.music.ui.components.Shelf
import ca.ilianokokoro.umihi.music.ui.components.ShelfCard
import ca.ilianokokoro.umihi.music.ui.components.ShelfHeader
import ca.ilianokokoro.umihi.music.ui.components.song.SongListItem
import ca.ilianokokoro.umihi.music.ui.screens.album.AlbumContextHeader
import ca.ilianokokoro.umihi.music.ui.screens.artist.ArtistHero
import ca.ilianokokoro.umihi.music.ui.screens.playlist.components.PlaylistHeader
import ca.ilianokokoro.umihi.music.ui.theme.StudioTestTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the album, artist and playlist pages to `app/build/outputs/theme-showcase`, to be looked
 * at. The real screens need their ViewModels, so this stacks their own header, rows and shelves.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class DetailPagesScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val songs = List(3) { index ->
        Song(
            youtubeId = "song-$index",
            title = "Titre $index",
            artist = "Daft Punk",
            duration = "3:4$index",
        )
    }

    private fun render(name: String, header: @Composable () -> Unit) {
        compose.setContent {
            StudioTestTheme(dark = true) {
                // The real screens sit in a Scaffold, which is what gives their text its colour.
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    item { header() }
                    items(songs, key = Song::youtubeId) { song ->
                        SongListItem(
                            song = song,
                            onPress = {},
                            playNext = {},
                            addToQueue = {},
                            startRadio = {},
                        )
                    }
                    item { ShelfHeader(title = "Du même artiste") }
                    item {
                        Shelf(title = null) {
                            items(3) { index ->
                                ShelfCard(
                                    title = "Album $index",
                                    subtitle = "2013",
                                    artworkUri = "",
                                    onClick = {},
                                )
                            }
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
    fun album() = render("detail-album") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PageGutter)
                .padding(top = 72.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AlbumContextHeader(
                coverUri = "",
                name = "Random Access Memories",
                artist = "Daft Punk",
                artistId = "artist",
                metadataLine = "Album • 2013 • 13 titres • 74 min",
                description = "",
                artworkSize = 220.dp,
                hasSongs = true,
                isLoggedIn = true,
                canSaveAlbum = true,
                isAlbumSaved = false,
                onOpenArtist = { _, _ -> },
                onToggleAlbumSaved = {},
                onPlay = {},
                onShuffle = {},
                onStartRadio = {},
                onDownload = {},
                onMore = {},
            )
        }
    }

    @Test
    fun artist() = render("detail-artist") {
        Box {
            ArtistHero(
                thumbnail = "",
                name = "Daft Punk",
                subscriberCount = "12 M d'abonnés",
            )
        }
    }

    @Test
    fun playlist() = render("detail-playlist") {
        PlaylistHeader(
            modifier = Modifier.padding(top = 72.dp),
            isDownloading = false,
            onOpenPlayer = {},
            onDownloadPlaylist = {},
            onDeleteDownloadPlaylist = {},
            onDeletePlaylist = {},
            onPlayPlaylist = {},
            onCancelDownload = {},
            onShufflePlaylist = {},
            playlist = Playlist(PlaylistInfo(id = "p", title = "Soirée"), songs),
        )
    }
}
