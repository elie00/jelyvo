package ca.ilianokokoro.umihi.music.ui.screens.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import ca.ilianokokoro.umihi.music.ui.components.studio.Switch

@Composable
fun BooleanSettingItem(
    title: String,
    subtitle: String,
    leadingIcon: ImageVector,
    value: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    SettingsRowLayout(
        title = title,
        subtitle = subtitle,
        leadingIcon = leadingIcon,
        modifier = Modifier.clickable { onToggle(!value) },
        trailing = {
            Switch(checked = value, onCheckedChange = onToggle)
        },
    )
}
