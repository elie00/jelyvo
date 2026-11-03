package ca.ilianokokoro.umihi.music.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.ButtonDefaults
import ca.ilianokokoro.umihi.music.ui.components.studio.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.ui.components.studio.TextButton
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

@Composable
fun ErrorMessage(
    modifier: Modifier = Modifier,
    ex: Exception,
    errorMessage: String? = null,
    onRetry: () -> Unit,
) {
    var showDetails by remember(ex) { mutableStateOf(false) }
    val message = errorMessage ?: friendlyErrorMessage(ex)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(
            space = 16.dp,
            alignment = Alignment.CenterVertically
        ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = stringResource(R.string.error),
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp)
        )

        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        FilledTonalButton(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        ) {
            Text(stringResource(R.string.retry))
        }

        TextButton(onClick = { showDetails = !showDetails }) {
            Text(
                stringResource(
                    if (showDetails) R.string.hide_technical_details
                    else R.string.show_technical_details
                )
            )
        }

        if (showDetails) {
            SelectionContainer {
                Text(
                    text = ex.localizedMessage
                        ?.take(600)
                        ?.ifBlank { ex::class.java.simpleName }
                        ?: ex::class.java.simpleName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun friendlyErrorMessage(exception: Exception): String {
    val technicalMessage = exception.localizedMessage.orEmpty()
    return when {
        exception is SocketTimeoutException ||
            "timeout" in technicalMessage.lowercase() ->
            stringResource(R.string.error_timeout)
        exception is UnknownHostException ||
            exception is IOException && "HTTP" !in technicalMessage.uppercase() ->
            stringResource(R.string.error_no_connection)
        Regex("""HTTP\s*(401|403)""").containsMatchIn(technicalMessage) ->
            stringResource(R.string.error_session_expired)
        Regex("""HTTP\s*429""").containsMatchIn(technicalMessage) ->
            stringResource(R.string.error_too_many_requests)
        Regex("""HTTP\s*(400|404)""").containsMatchIn(technicalMessage) ||
            "invalid_argument" in technicalMessage.lowercase() ->
            stringResource(R.string.error_content_unavailable)
        else -> stringResource(R.string.error_generic)
    }
}
