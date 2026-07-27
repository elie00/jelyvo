package ca.ilianokokoro.umihi.music.ui.screens.player.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Visualiseur audio réactif composé de 12 barres de spectre sinusoïdales animées
 * affichées dans le lecteur plein écran lorsque la musique est en cours de lecture.
 */
@Composable
fun AudioVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val barCount = 12
    val animatables = remember { List(barCount) { Animatable(0.2f) } }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            animatables.forEachIndexed { index, animatable ->
                val baseDuration = 320 + (index % 5) * 90
                val delayTime = ((index % 4) * 70).toLong()
                delay(delayTime)
                animatable.animateTo(
                    targetValue = 0.95f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = baseDuration, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
            }
        } else {
            animatables.forEach { it.animateTo(0.15f, tween(300)) }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        animatables.forEach { animatable ->
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height((28 * animatable.value).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color.copy(alpha = 0.85f))
            )
        }
    }
}
