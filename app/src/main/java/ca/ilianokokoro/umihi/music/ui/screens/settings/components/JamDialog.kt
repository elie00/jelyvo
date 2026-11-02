package ca.ilianokokoro.umihi.music.ui.screens.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.jam.JamError
import ca.ilianokokoro.umihi.music.core.jam.JamStatus
import ca.ilianokokoro.umihi.music.core.jam.normalizeJamServerUrl

/** Creates or joins a listening party, or shows the current one with a way to leave. */
@Composable
fun JamDialog(
    status: JamStatus,
    savedServerUrl: String,
    onCreate: (serverUrl: String) -> Unit,
    onJoin: (serverUrl: String, code: String) -> Unit,
    onLeave: () -> Unit,
    onClose: () -> Unit,
) {
    var serverUrl by rememberSaveable(savedServerUrl) { mutableStateOf(savedServerUrl) }
    var code by rememberSaveable { mutableStateOf("") }
    val connecting = status is JamStatus.Connecting
    val validUrl = normalizeJamServerUrl(serverUrl)

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.jam_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (status is JamStatus.InParty) {
                    Text(stringResource(R.string.jam_share_code, status.code))
                    Text(
                        text = stringResource(R.string.jam_members, status.members.size),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(stringResource(R.string.jam_hint))
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = { Text(stringResource(R.string.jam_server_label)) },
                        singleLine = true,
                        enabled = !connecting,
                        isError = serverUrl.isNotBlank() && validUrl == null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text(stringResource(R.string.jam_code_label)) },
                        singleLine = true,
                        enabled = !connecting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    (status as? JamStatus.Failed)?.let { failed ->
                        Text(
                            text = stringResource(
                                when (failed.error) {
                                    JamError.Unreachable -> R.string.jam_error_unreachable
                                    JamError.UnknownCode -> R.string.jam_error_unknown_code
                                    JamError.PartyFull -> R.string.jam_error_full
                                    JamError.Refused -> R.string.jam_error_refused
                                }
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (status is JamStatus.InParty) {
                TextButton(onClick = onLeave) { Text(stringResource(R.string.jam_leave)) }
            } else {
                Row {
                    TextButton(
                        onClick = { validUrl?.let(onCreate) },
                        enabled = validUrl != null && !connecting,
                    ) {
                        Text(stringResource(R.string.jam_create))
                    }
                    TextButton(
                        onClick = { validUrl?.let { onJoin(it, code) } },
                        enabled = validUrl != null && code.isNotBlank() && !connecting,
                    ) {
                        Text(stringResource(R.string.jam_join))
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
        },
    )
}
