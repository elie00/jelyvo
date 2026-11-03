package ca.ilianokokoro.umihi.music.ui.navigation

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.jam.JamManager
import ca.ilianokokoro.umihi.music.core.jam.JamStatus
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository
import ca.ilianokokoro.umihi.music.ui.screens.settings.components.jamEndMessage
import ca.ilianokokoro.umihi.music.ui.screens.settings.components.jamErrorMessage
import kotlinx.coroutines.launch

/**
 * What the listening party has to say outside its own dialog, wherever the listener is: an
 * invitation to accept, and a party ending under them.
 */
@Composable
fun JamInviteHost() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val invite by JamManager.pendingInvite.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        JamManager.status.collect { status ->
            if (status is JamStatus.Ended) {
                Toast.makeText(context, jamEndMessage(status.end), Toast.LENGTH_LONG).show()
            }
        }
    }

    val current = invite ?: return
    AlertDialog(
        onDismissRequest = JamManager::dismissInvite,
        title = { Text(stringResource(R.string.jam_invite_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.jam_invite_message, current.code))
                // A link can name any server, and that server would see who is listening to what.
                if (!current.isOnDefaultServer) {
                    Text(
                        text = stringResource(R.string.jam_invite_other_server, current.serverHost),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    JamManager.dismissInvite()
                    scope.launch {
                        JamManager.join(
                            serverUrl = current.serverUrl,
                            code = current.code,
                            identity = DatastoreRepository(context).jamIdentity(),
                        )
                        val failed = JamManager.status.value as? JamStatus.Failed
                        if (failed != null) {
                            Toast.makeText(context, jamErrorMessage(failed.error), Toast.LENGTH_LONG)
                                .show()
                        }
                    }
                },
            ) { Text(stringResource(R.string.jam_join)) }
        },
        dismissButton = {
            TextButton(onClick = JamManager::dismissInvite) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
