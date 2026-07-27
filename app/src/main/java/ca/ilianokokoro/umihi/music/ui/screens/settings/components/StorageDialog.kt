package ca.ilianokokoro.umihi.music.ui.screens.settings.components

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.StorageCategory
import ca.ilianokokoro.umihi.music.core.StorageUsage

/** Label shown for each purgeable storage category. */
@Composable
fun storageCategoryLabel(category: StorageCategory): String {
    return stringResource(
        when (category) {
            StorageCategory.Images -> R.string.storage_category_images
            StorageCategory.Downloads -> R.string.storage_category_downloads
            StorageCategory.Database -> R.string.storage_category_database
            StorageCategory.ApiCache -> R.string.storage_category_api_cache
        }
    )
}

/**
 * Per-category disk usage with a purge action. Sizes are measured off the main thread; while
 * [usage] is null every row shows the "calculating…" placeholder. The database row is measured
 * only (it holds playlists and history), so it carries no purge button.
 */
@Composable
fun StorageDialog(
    usage: StorageUsage?,
    onPurge: (StorageCategory) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Text(text = stringResource(R.string.storage_title))
        },
        text = {
            Column {
                StorageCategory.entries.forEach { category ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = storageCategoryLabel(category),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = if (usage == null) {
                                    stringResource(R.string.storage_computing)
                                } else {
                                    Formatter.formatShortFileSize(
                                        context,
                                        usage.bytesFor(category),
                                    )
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (category != StorageCategory.Database) {
                            val purgeEnabled =
                                usage != null && usage.bytesFor(category) > 0
                            IconButton(
                                onClick = { onPurge(category) },
                                enabled = purgeEnabled,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = stringResource(
                                        R.string.storage_purge_action,
                                        storageCategoryLabel(category),
                                    ),
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
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.close))
            }
        },
    )
}
