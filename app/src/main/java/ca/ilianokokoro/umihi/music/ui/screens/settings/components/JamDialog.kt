package ca.ilianokokoro.umihi.music.ui.screens.settings.components

import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.jam.JamEnd
import ca.ilianokokoro.umihi.music.core.jam.JamError
import ca.ilianokokoro.umihi.music.core.jam.JamInvites
import ca.ilianokokoro.umihi.music.core.jam.JamStatus
import ca.ilianokokoro.umihi.music.core.jam.PartyMember
import ca.ilianokokoro.umihi.music.core.jam.normalizeJamServerUrl
import ca.ilianokokoro.umihi.music.ui.components.QrCode

/** Creates or joins a listening party, or shows the current one and who is in it. */
@Composable
fun JamDialog(
    status: JamStatus,
    savedServerUrl: String,
    onCreate: (serverUrl: String) -> Unit,
    onJoin: (serverUrl: String, code: String) -> Unit,
    onLeave: () -> Unit,
    onKick: (memberId: String) -> Unit,
    onSetHostOnlyControl: (Boolean) -> Unit,
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
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                if (status is JamStatus.InParty) {
                    JamPartyContent(status, onKick, onSetHostOnlyControl)
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
                    val problem = when (status) {
                        is JamStatus.Failed -> stringResource(jamErrorMessage(status.error))
                        is JamStatus.Ended -> stringResource(jamEndMessage(status.end))
                        else -> null
                    }
                    if (problem != null) {
                        Text(
                            text = problem,
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

/** The invitation, then who is here, then what the host may do about it. */
@Composable
internal fun JamPartyContent(
    party: JamStatus.InParty,
    onKick: (memberId: String) -> Unit,
    onSetHostOnlyControl: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val link = JamInvites.link(party.serverUrl, party.code)
    val inviteText = stringResource(R.string.jam_invite_share_text, link)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        QrCode(
            text = link,
            description = stringResource(R.string.jam_invite_qr_description),
            modifier = Modifier.size(192.dp),
        )
        SelectionContainer {
            Text(party.code, style = MaterialTheme.typography.headlineSmall)
        }
        FilledTonalButton(
            onClick = {
                val send = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, inviteText)
                context.startActivity(Intent.createChooser(send, null))
            },
        ) {
            Icon(Icons.Outlined.Share, contentDescription = null)
            Text(
                text = stringResource(R.string.jam_invite_share),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }

    Text(
        text = stringResource(R.string.jam_members, party.members.size),
        style = MaterialTheme.typography.titleSmall,
    )
    party.members.forEach { member ->
        JamMemberRow(
            member = member,
            isYou = member.memberId == party.myMemberId,
            onKick = if (party.isHost && member.memberId != party.myMemberId) {
                { onKick(member.memberId) }
            } else {
                null
            },
        )
    }

    if (party.isHost) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.jam_host_only_control),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
            )
            Switch(checked = party.hostOnlyControl, onCheckedChange = onSetHostOnlyControl)
        }
    } else if (party.hostOnlyControl) {
        Text(
            text = stringResource(R.string.jam_host_controls_music),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun JamMemberRow(member: PartyMember, isYou: Boolean, onKick: (() -> Unit)?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        // Someone between connections keeps their place, dimmed: they are expected back.
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (member.connected) 1f else 0.5f),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
        ) {
            Text(
                text = member.displayName.take(1).uppercase(),
                style = MaterialTheme.typography.titleSmall,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = member.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val roles = listOfNotNull(
                stringResource(R.string.jam_member_you).takeIf { isYou },
                stringResource(R.string.jam_member_host).takeIf { member.isHost },
            )
            if (roles.isNotEmpty()) {
                Text(
                    text = roles.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (onKick != null) {
            IconButton(onClick = onKick) {
                Icon(
                    imageVector = Icons.Outlined.PersonRemove,
                    contentDescription = stringResource(R.string.jam_kick, member.displayName),
                )
            }
        }
    }
}

@StringRes
fun jamErrorMessage(error: JamError): Int = when (error) {
    JamError.Unreachable -> R.string.jam_error_unreachable
    JamError.UnknownCode -> R.string.jam_error_unknown_code
    JamError.PartyFull -> R.string.jam_error_full
    JamError.Busy -> R.string.jam_error_busy
    JamError.Refused -> R.string.jam_error_refused
}

@StringRes
fun jamEndMessage(end: JamEnd): Int = when (end) {
    JamEnd.Kicked -> R.string.jam_ended_kicked
    JamEnd.ServerQuota -> R.string.jam_ended_quota
    JamEnd.Gone -> R.string.jam_ended_gone
}
