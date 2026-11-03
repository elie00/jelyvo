package ca.ilianokokoro.umihi.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.ui.components.studio.CircleIconButton

@Composable
fun BackButton(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CircleIconButton(
        icon = Icons.AutoMirrored.Outlined.ArrowBack,
        contentDescription = stringResource(R.string.back_description),
        onClick = onBack,
        modifier = modifier.background(MaterialTheme.colorScheme.background.copy(alpha = 0.45f), CircleShape),
        size = 44.dp,
    )
}
