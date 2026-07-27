package ca.ilianokokoro.umihi.music.ui.components.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.WatchLater
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import ca.ilianokokoro.umihi.music.core.Constants

/**
 * Les playlists automatiques (telechargements, titres likes, episodes de podcasts) n'ont pas de
 * vraie pochette : YouTube sert un visuel generique et les telechargements n'en ont aucun. Chacune
 * recoit a la place une tuile aux couleurs de la marque — degrade creme/corail/aubergine du logo —
 * avec une icone qui dit son contenu.
 */
enum class SpecialPlaylistCover {
    Downloads,
    Liked,
    NewEpisodes,
    EpisodesForLater,
}

/** Le type de pochette de marque pour cet id de playlist, ou null pour une playlist ordinaire. */
fun specialPlaylistCover(playlistId: String): SpecialPlaylistCover? = when (playlistId) {
    Constants.Downloads.DOWNLOADED_PLAYLIST_ID -> SpecialPlaylistCover.Downloads
    "LM", Constants.YoutubeApi.Browse.LIKED_SONGS_BROWSE_ID -> SpecialPlaylistCover.Liked
    Constants.YoutubeApi.PODCAST_PLAYLIST_ID -> SpecialPlaylistCover.NewEpisodes
    // « À ecouter plus tard » : id observe cote YT Music pour les episodes sauvegardes.
    "VLLL", "LL" -> SpecialPlaylistCover.EpisodesForLater
    else -> null
}

// Palette de la marque (voir branding/jelyvo-mark.svg et icon_background.xml).
private val Cream = Color(0xFFFFF8ED)
private val Coral = Color(0xFFF47B67)
private val Aubergine = Color(0xFF74304F)

@Composable
fun JelyvoPlaylistCover(
    cover: SpecialPlaylistCover,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val (gradient, iconTint, icon) = when (cover) {
        SpecialPlaylistCover.Downloads -> Triple(
            listOf(Cream, Coral.copy(alpha = 0.55f)), Aubergine, Icons.Rounded.Download
        )

        SpecialPlaylistCover.Liked -> Triple(
            listOf(Coral, Aubergine), Cream, Icons.Rounded.Favorite
        )

        SpecialPlaylistCover.NewEpisodes -> Triple(
            listOf(Aubergine, Color(0xFF3D1A2C)), Coral, Icons.Rounded.Podcasts
        )

        SpecialPlaylistCover.EpisodesForLater -> Triple(
            listOf(Cream, Aubergine.copy(alpha = 0.45f)), Aubergine, Icons.Rounded.WatchLater
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium)
            .background(Brush.linearGradient(gradient)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.fillMaxSize(0.45f)
        )
    }
}
