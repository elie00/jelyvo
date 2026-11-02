package ca.ilianokokoro.umihi.music.ui.screens.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

@Composable
fun BooleanSettingItem(
    title: String,
    subtitle: String,
    leadingIcon: ImageVector,
    value: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    SettingsRowLayout(
        title = title,
        subtitle = subtitle,
        leadingIcon = leadingIcon,
        modifier = Modifier.clickable {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            onToggle(!value)
        },
        trailing = {
            Switch(
                checked = value,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    onToggle(it)
                },
            )
        },
    )
}
