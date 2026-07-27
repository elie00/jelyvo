package ca.ilianokokoro.umihi.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

/**
 * Composant d'avatar réutilisable :
 * - Si une photo personnelle existe ([avatarPath]), elle est affichée avec une découpe ronde parfaite.
 * - Sinon, un monogramme stylisé avec la première lettre de [name] est généré sur un fond dégradé vibrant.
 */
@Composable
fun ProfileAvatar(
    name: String,
    avatarPath: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    if (avatarPath.isNotBlank()) {
        AsyncImage(
            model = avatarPath,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )
    } else {
        val initial = name.trim().take(1).uppercase().ifBlank { "J" }
        val gradientBrush = Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.tertiary,
            )
        )

        val fontSize = (size.value * 0.44f).sp

        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(gradientBrush),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = fontSize,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}
