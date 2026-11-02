package ca.ilianokokoro.umihi.music.ui.screens.search.components

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.ui.components.materialu.MaterialUInput

@Composable
fun SearchBar(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    onVoiceSearch: (() -> Unit)? = null,
    onClear: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    focusManager: FocusManager? = null,
) {

    var searchModifier = modifier
        .fillMaxWidth()
        .minimumInteractiveComponentSize()

    if (focusRequester != null) {
        searchModifier = searchModifier
            .focusRequester(focusRequester)
    }

    // A non-empty query shows a clear button (when supported); otherwise voice search.
    val showClear = onClear != null && value.isNotEmpty()

    MaterialUInput(
        modifier = searchModifier,
        value = value,
        onValueChange = onValueChange,
        leadingIcon = Icons.Rounded.Search,
        trailingIcon = when {
            showClear -> Icons.Rounded.Close
            onVoiceSearch != null -> Icons.Rounded.Mic
            else -> null
        },
        onTrailingIconClick = if (showClear) onClear else onVoiceSearch,
        placeholder = stringResource(R.string.search),
        shape = RoundedCornerShape(percent = 50),
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            errorContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Search
        ),
        keyboardActions = KeyboardActions(
            onSearch = {
                focusManager?.clearFocus()
                onSearch()
            }
        ),
        maxLines = 1
    )

}