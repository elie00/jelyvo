package ca.ilianokokoro.umihi.music.ui.components.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.ThemeColor
import ca.ilianokokoro.umihi.music.ui.theme.SeedSchemes


@Composable
fun themeColorLabel(themeColor: ThemeColor): String {
    return stringResource(
        when (themeColor) {
            ThemeColor.Dynamic -> R.string.theme_color_dynamic
            ThemeColor.OceanBlue -> R.string.theme_color_ocean_blue
            ThemeColor.ForestGreen -> R.string.theme_color_forest_green
            ThemeColor.Violet -> R.string.theme_color_violet
            ThemeColor.SunsetOrange -> R.string.theme_color_sunset_orange
            ThemeColor.RoseRed -> R.string.theme_color_rose_red
            ThemeColor.Mono -> R.string.theme_color_mono
        }
    )
}

@Composable
fun ThemeColorDialog(
    selectedOption: ThemeColor,
    onChange: (newThemeColor: ThemeColor) -> Unit,
    onClose: () -> Unit
) {
    val darkTheme = isSystemInDarkTheme()

    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Text(
                text = stringResource(R.string.theme_color_title),
            )
        },
        text = {
            val radioOptions = ThemeColor.entries
            Column(Modifier.selectableGroup()) {
                radioOptions.forEach { option ->
                    val isSelected = option == selectedOption
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(shape = RoundedCornerShape(16.dp))
                            .selectable(
                                selected = isSelected,
                                onClick = {
                                    onChange(option)
                                    onClose()
                                },
                                role = Role.RadioButton
                            )
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = null
                        )
                        Text(
                            text = themeColorLabel(option),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .padding(start = 16.dp)
                                .weight(1f)
                        )
                        SeedSchemes.previewColor(option, darkTheme)?.let { swatch ->
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(swatch)
                            )
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
