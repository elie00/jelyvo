package ca.ilianokokoro.umihi.music.ui.screens.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.ui.components.studio.ALERT_WIDTH_WIDE
import ca.ilianokokoro.umihi.music.ui.components.studio.AlertDialog
import ca.ilianokokoro.umihi.music.ui.components.studio.OutlinedTextField
import ca.ilianokokoro.umihi.music.ui.components.studio.TextButton

/** Connects a ListenBrainz account, or disconnects it when one is already connected. */
@Composable
fun ListenBrainzDialog(
    connected: Boolean,
    connecting: Boolean,
    errorMessage: Int?,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onClose: () -> Unit,
) {
    var token by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        width = ALERT_WIDTH_WIDE,
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.listenbrainz_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (connected) {
                    Text(stringResource(R.string.listenbrainz_connected))
                } else {
                    Text(stringResource(R.string.listenbrainz_token_hint))
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text(stringResource(R.string.listenbrainz_token_label)) },
                        singleLine = true,
                        enabled = !connecting,
                        isError = errorMessage != null,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    errorMessage?.let {
                        Text(
                            text = stringResource(it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (connected) {
                TextButton(onClick = onDisconnect) {
                    Text(stringResource(R.string.listenbrainz_disconnect))
                }
            } else {
                TextButton(
                    onClick = { onConnect(token) },
                    enabled = token.isNotBlank() && !connecting,
                ) {
                    Text(stringResource(R.string.listenbrainz_connect))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) {
                Text(stringResource(R.string.close))
            }
        },
    )
}
