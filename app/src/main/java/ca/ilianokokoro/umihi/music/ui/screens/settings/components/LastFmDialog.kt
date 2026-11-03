package ca.ilianokokoro.umihi.music.ui.screens.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.ui.components.studio.ALERT_WIDTH_WIDE
import ca.ilianokokoro.umihi.music.ui.components.studio.AlertDialog
import ca.ilianokokoro.umihi.music.ui.components.studio.OutlinedTextField
import ca.ilianokokoro.umihi.music.ui.components.studio.TextButton

/**
 * Connects a Last.fm account in two steps: the user's own API key and secret, then approval in the
 * browser. Disconnects instead when an account is already connected.
 */
@Composable
fun LastFmDialog(
    connected: Boolean,
    busy: Boolean,
    errorMessage: Int?,
    authorizeUrl: String?,
    onRequestApproval: (apiKey: String, secret: String) -> Unit,
    onApproved: () -> Unit,
    onDisconnect: () -> Unit,
    onClose: () -> Unit,
) {
    var apiKey by rememberSaveable { mutableStateOf("") }
    var secret by rememberSaveable { mutableStateOf("") }
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(authorizeUrl) {
        authorizeUrl?.let(uriHandler::openUri)
    }

    AlertDialog(
        width = ALERT_WIDTH_WIDE,
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.lastfm_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                when {
                    connected -> Text(stringResource(R.string.lastfm_connected))

                    authorizeUrl != null -> Text(stringResource(R.string.lastfm_approve_hint))

                    else -> {
                        Text(stringResource(R.string.lastfm_keys_hint))
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text(stringResource(R.string.lastfm_api_key_label)) },
                            singleLine = true,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = secret,
                            onValueChange = { secret = it },
                            label = { Text(stringResource(R.string.lastfm_secret_label)) },
                            singleLine = true,
                            enabled = !busy,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                errorMessage?.let {
                    Text(
                        text = stringResource(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            when {
                connected -> TextButton(onClick = onDisconnect) {
                    Text(stringResource(R.string.lastfm_disconnect))
                }

                authorizeUrl != null -> TextButton(onClick = onApproved, enabled = !busy) {
                    Text(stringResource(R.string.lastfm_finish))
                }

                else -> TextButton(
                    onClick = { onRequestApproval(apiKey, secret) },
                    enabled = apiKey.isNotBlank() && secret.isNotBlank() && !busy,
                ) {
                    Text(stringResource(R.string.lastfm_authorize))
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
