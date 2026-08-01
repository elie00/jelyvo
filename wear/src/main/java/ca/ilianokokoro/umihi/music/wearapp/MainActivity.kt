package ca.ilianokokoro.umihi.music.wearapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import ca.ilianokokoro.umihi.music.wear.WearPlaybackSnapshot
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val viewModel: WearPlaybackViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            JelyvoWearTheme {
                NowPlayingScreen(
                    state = state,
                    onPrevious = viewModel::previous,
                    onPlayPause = viewModel::togglePlayback,
                    onNext = viewModel::next,
                )
            }
        }
    }
}

@Composable
private fun JelyvoWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}

@Composable
private fun NowPlayingScreen(
    state: WearUiState,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    val playback = state.playback
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090909)),
    ) {
        if (playback.artworkUri.isNotBlank()) {
            AsyncImage(
                model = playback.artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        drawContent()
                        drawRect(Color.Black.copy(alpha = 0.72f))
                        drawRect(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.18f),
                                1f to Color.Black.copy(alpha = 0.88f),
                            ),
                        )
                    },
            )
        }

        if (playback.hasMedia) {
            PlaybackContent(
                playback = playback,
                connected = state.phoneConnected,
                onPrevious = onPrevious,
                onPlayPause = onPlayPause,
                onNext = onNext,
            )
        } else {
            EmptyContent(connected = state.phoneConnected, loading = state.loading)
        }
    }
}

@Composable
private fun PlaybackContent(
    playback: WearPlaybackSnapshot,
    connected: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ConnectionLabel(connected)
        Spacer(Modifier.height(5.dp))
        ArtworkWithProgress(playback)
        Spacer(Modifier.height(7.dp))
        Text(
            text = playback.title.ifBlank { "Titre inconnu" },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = Color.White,
            fontSize = 15.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = playback.artist.ifBlank { "Artiste inconnu" },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = Color.White.copy(alpha = 0.68f),
            fontSize = 12.sp,
            lineHeight = 14.sp,
        )
        Spacer(Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TransportButton(
                enabled = connected && playback.hasPrevious,
                onClick = onPrevious,
                size = 42,
            ) {
                Icon(Icons.Filled.SkipPrevious, "Titre précédent", tint = Color.White)
            }
            Spacer(Modifier.width(10.dp))
            TransportButton(
                enabled = connected,
                onClick = onPlayPause,
                size = 58,
                emphasized = true,
            ) {
                AnimatedContent(playback.isPlaying, label = "play-pause") { playing ->
                    Icon(
                        imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Lecture",
                        tint = Color.White,
                        modifier = Modifier.size(31.dp),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            TransportButton(
                enabled = connected && playback.hasNext,
                onClick = onNext,
                size = 42,
            ) {
                Icon(Icons.Filled.SkipNext, "Titre suivant", tint = Color.White)
            }
        }
    }
}

@Composable
private fun ArtworkWithProgress(playback: WearPlaybackSnapshot) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(playback.isPlaying, playback.updatedAtMs) {
        while (playback.isPlaying) {
            nowMs = System.currentTimeMillis()
            delay(500)
        }
    }
    val position = playback.estimatedPositionMs(nowMs)
    val fraction = if (playback.durationMs > 0L) {
        position.toFloat() / playback.durationMs.toFloat()
    } else 0f

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(102.dp)) {
        AsyncImage(
            model = playback.artworkUri.takeIf(String::isNotBlank),
            contentDescription = "Pochette de ${playback.title}",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(91.dp)
                .clip(CircleShape)
                .background(Color(0xFF272727)),
        )
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 3.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = Color.White.copy(alpha = 0.2f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = Accent,
                startAngle = -90f,
                sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun ConnectionLabel(connected: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(5.dp)
                .background(if (connected) Accent else Color(0xFF777777), CircleShape),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = if (connected) "TÉLÉPHONE" else "HORS CONNEXION",
            color = Color.White.copy(alpha = 0.64f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.9.sp,
        )
    }
}

@Composable
private fun EmptyContent(connected: Boolean, loading: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .background(Accent.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("J", color = Accent, fontSize = 34.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(13.dp))
        Text(
            text = when {
                loading -> "Connexion…"
                !connected -> "Téléphone indisponible"
                else -> "Aucune lecture"
            },
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = if (connected) {
                "Lancez un titre dans Jelyvo"
            } else {
                "Rapprochez la montre du téléphone"
            },
            color = Color.White.copy(alpha = 0.58f),
            fontSize = 11.sp,
            lineHeight = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TransportButton(
    enabled: Boolean,
    onClick: () -> Unit,
    size: Int,
    emphasized: Boolean = false,
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(
                color = when {
                    !enabled -> Color.White.copy(alpha = 0.06f)
                    emphasized -> Accent
                    else -> Color.White.copy(alpha = 0.14f)
                },
                shape = CircleShape,
            )
            .clickable(enabled = enabled) {
                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size((size * 0.55f).dp), contentAlignment = Alignment.Center) {
            content()
        }
    }
}

private val Accent = Color(0xFFE8492A)
