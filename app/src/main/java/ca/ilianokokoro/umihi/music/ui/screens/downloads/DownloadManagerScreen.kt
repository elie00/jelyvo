@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ca.ilianokokoro.umihi.music.ui.screens.downloads

import android.app.Application
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.WorkInfo
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.ui.components.BackButton

@Composable
fun DownloadManagerScreen(
    application: Application,
    onBack: () -> Unit,
    viewModel: DownloadManagerViewModel = viewModel(
        factory = DownloadManagerViewModel.Factory(application),
    ),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.download_manager_title)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                DownloadSummary(state)
            }
            if (state.active.isNotEmpty()) {
                item {
                    SectionTitle(
                        stringResource(R.string.download_manager_active),
                        action = {
                            TextButton(onClick = viewModel::cancelAll) {
                                Text(stringResource(R.string.download_manager_cancel_all))
                            }
                        },
                    )
                }
                items(state.active, key = { it.id }) { task ->
                    DownloadTaskCard(task, onCancel = { viewModel.cancel(task.id) })
                }
            }
            if (state.issues.isNotEmpty()) {
                item {
                    SectionTitle(
                        stringResource(R.string.download_manager_issues),
                        action = {
                            TextButton(onClick = viewModel::retryAll) {
                                Text(stringResource(R.string.download_manager_retry_all))
                            }
                        },
                    )
                }
                items(state.issues, key = { it.id }) { task ->
                    DownloadTaskCard(task, onRetry = { viewModel.retry(task.id) })
                }
            }
            if (!state.loading && state.active.isEmpty() && state.issues.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Outlined.CloudDone, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(10.dp))
                        Text(stringResource(R.string.download_manager_empty))
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadSummary(state: DownloadManagerState) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icons.Outlined.CloudDownload, null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(
                    stringResource(R.string.download_manager_available, state.downloadedCount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (state.loading) {
                        stringResource(R.string.storage_computing)
                    } else {
                        Formatter.formatShortFileSize(context, state.storageBytes)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, action: @Composable (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
private fun DownloadTaskCard(
    task: DownloadManagerTask,
    onCancel: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (task.hasIssue) Icons.Outlined.ErrorOutline else Icons.Outlined.CloudDownload,
                    null,
                    tint = if (task.hasIssue) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                )
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(task.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        taskStatus(task),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                onCancel?.let {
                    IconButton(onClick = it) {
                        Icon(Icons.Outlined.Cancel, stringResource(R.string.cancel_download))
                    }
                }
                onRetry?.let {
                    IconButton(onClick = it) {
                        Icon(Icons.Outlined.Refresh, stringResource(R.string.retry))
                    }
                }
            }
            if (task.isActive) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { task.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun taskStatus(task: DownloadManagerTask): String = when {
    task.state == WorkInfo.State.BLOCKED -> stringResource(R.string.download_manager_waiting)
    task.state == WorkInfo.State.ENQUEUED -> stringResource(R.string.download_manager_queued)
    task.state == WorkInfo.State.RUNNING -> stringResource(
        R.string.download_manager_progress,
        task.completed,
        task.total,
    )
    task.failed > 0 -> stringResource(R.string.download_manager_partial, task.failed)
    else -> stringResource(R.string.download_manager_failed)
}
