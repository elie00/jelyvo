package ca.ilianokokoro.umihi.music.ui.components.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import ca.ilianokokoro.umihi.music.ui.components.studio.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.ui.components.studio.AlertDialog
import ca.ilianokokoro.umihi.music.ui.components.studio.TextButton
import ca.ilianokokoro.umihi.music.ui.theme.ThemePreset
import ca.ilianokokoro.umihi.music.ui.theme.ThemePresets


@Composable
fun themePresetLabel(preset: ThemePreset): String {
    return stringResource(
        when (preset) {
            ThemePreset.Studio -> R.string.theme_preset_studio
            ThemePreset.Jelyvo -> R.string.theme_preset_jelyvo
            ThemePreset.Minuit -> R.string.theme_preset_minuit
            ThemePreset.Vinyle -> R.string.theme_preset_vinyle
            ThemePreset.Neon -> R.string.theme_preset_neon
            ThemePreset.Oled -> R.string.theme_preset_oled
        }
    )
}

@Composable
private fun themePresetDescription(preset: ThemePreset): String {
    return stringResource(
        when (preset) {
            ThemePreset.Studio -> R.string.theme_preset_studio_description
            ThemePreset.Jelyvo -> R.string.theme_preset_jelyvo_description
            ThemePreset.Minuit -> R.string.theme_preset_minuit_description
            ThemePreset.Vinyle -> R.string.theme_preset_vinyle_description
            ThemePreset.Neon -> R.string.theme_preset_neon_description
            ThemePreset.Oled -> R.string.theme_preset_oled_description
        }
    )
}

/**
 * Selecteur de theme complet. Chaque ligne montre le trio de couleurs du preset (fond, primaire,
 * tertiaire) tel qu'il rendrait dans le mode clair/sombre courant.
 */
@Composable
fun ThemePresetDialog(
    selectedOption: ThemePreset,
    onChange: (newPreset: ThemePreset) -> Unit,
    onClose: () -> Unit
) {
    val darkTheme = isSystemInDarkTheme()

    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Text(
                text = stringResource(R.string.theme_preset_title),
            )
        },
        text = {
            Column(Modifier.selectableGroup()) {
                ThemePreset.entries.forEach { option ->
                    val isSelected = option == selectedOption
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(shape = RoundedCornerShape(16.dp))
                            .selectable(
                                selected = isSelected,
                                onClick = {
                                    onChange(option)
                                    onClose()
                                },
                                role = Role.RadioButton
                            )
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = null
                        )
                        Column(
                            modifier = Modifier
                                .padding(start = 16.dp)
                                .weight(1f)
                        ) {
                            Text(
                                text = themePresetLabel(option),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = themePresetDescription(option),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val (background, primary, tertiary) =
                            ThemePresets.previewColors(option, darkTheme)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(background, primary, tertiary).forEach { swatch ->
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(swatch)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onClose,
                shapes = ButtonDefaults.shapes()
            )
            {
                Text(stringResource(R.string.close))
            }
        },
        properties = DialogProperties(dismissOnClickOutside = false)
    )
}
