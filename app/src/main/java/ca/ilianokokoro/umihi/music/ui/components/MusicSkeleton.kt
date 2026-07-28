package ca.ilianokokoro.umihi.music.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class MusicSkeletonLayout {
    Home,
    Search,
    Library,
    SongList,
}

/**
 * Screen-shaped loading state. Keeping the final content's geometry visible prevents the
 * spinner-to-page jump that was especially noticeable on slower phones and mobile networks.
 */
@Composable
fun MusicSkeleton(
    layout: MusicSkeletonLayout,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "musicSkeleton")
    val pulse by transition.animateFloat(
        initialValue = 0.42f,
        targetValue = 0.78f,
        animationSpec = infiniteRepeatable(
            animation = tween(850),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "musicSkeletonPulse",
    )
    val color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = pulse)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when (layout) {
            MusicSkeletonLayout.Home -> {
                SkeletonBlock(color, 156.dp, 26.dp, 0.54f)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(3) { SkeletonBlock(color, 112.dp, 18.dp, 1f) }
                }
                SkeletonBlock(color, 18.dp, 8.dp, 0.38f)
                repeat(4) { SkeletonSongRow(color) }
            }
            MusicSkeletonLayout.Search -> {
                SkeletonBlock(color, 52.dp, 22.dp, 1f)
                SkeletonBlock(color, 18.dp, 8.dp, 0.45f)
                repeat(6) { SkeletonSongRow(color) }
            }
            MusicSkeletonLayout.Library -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(3) { SkeletonBlock(color, 104.dp, 20.dp, 1f) }
                }
                repeat(6) { SkeletonSongRow(color) }
            }
            MusicSkeletonLayout.SongList -> {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    SkeletonBlock(color, 132.dp, 24.dp, 0.36f)
                    Column(
                        modifier = Modifier.padding(top = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SkeletonBlock(color, 24.dp, 8.dp, 0.92f)
                        SkeletonBlock(color, 16.dp, 8.dp, 0.66f)
                        SkeletonBlock(color, 42.dp, 20.dp, 0.48f)
                    }
                }
                repeat(6) { SkeletonSongRow(color) }
            }
        }
    }
}

@Composable
private fun SkeletonSongRow(color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SkeletonBlock(color, 56.dp, 12.dp, 0.16f)
        Column(
            modifier = Modifier.padding(top = 7.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            SkeletonBlock(color, 16.dp, 7.dp, 0.78f)
            SkeletonBlock(color, 13.dp, 7.dp, 0.52f)
        }
    }
}

@Composable
private fun SkeletonBlock(
    color: Color,
    height: androidx.compose.ui.unit.Dp,
    radius: androidx.compose.ui.unit.Dp,
    widthFraction: Float,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(radius))
            .background(color),
    )
}
