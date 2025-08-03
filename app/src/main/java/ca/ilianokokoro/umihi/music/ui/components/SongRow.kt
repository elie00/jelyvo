package ca.ilianokokoro.umihi.music.ui.components

import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ca.ilianokokoro.umihi.music.models.Song

@Composable
fun SongRow(song: Song, modifier: Modifier = Modifier) {
    Card { Text(song.title) }
}