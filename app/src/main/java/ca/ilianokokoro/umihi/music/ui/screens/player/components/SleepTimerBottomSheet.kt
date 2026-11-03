package ca.ilianokokoro.umihi.music.ui.screens.player.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.ui.components.studio.ActionPill
import ca.ilianokokoro.umihi.music.ui.components.studio.ModalBottomSheet
import ca.ilianokokoro.umihi.music.ui.components.studio.OutlinedTextField
import ca.ilianokokoro.umihi.music.ui.components.studio.Slider
import kotlin.math.roundToInt

@Composable
fun SleepTimerBottomSheet(
    changeVisibility: (visible: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    activeRemainingSeconds: Long?,
    defaultMinutes: Int = Constants.Ui.Player.SleepTimer.DEFAULT_VALUE,
    onStartTimer: (minutes: Int) -> Unit,
    onStartEndOfSong: () -> Unit,
    onCancelTimer: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = { changeVisibility(false) },
        title = stringResource(
            if (activeRemainingSeconds != null) R.string.sleep_timer_active else R.string.sleep_timer
        ),
    ) {
        SleepTimerContent(
            changeVisibility = changeVisibility,
            activeRemainingSeconds = activeRemainingSeconds,
            defaultMinutes = defaultMinutes,
            onStartTimer = onStartTimer,
            onStartEndOfSong = onStartEndOfSong,
            onCancelTimer = onCancelTimer,
            modifier = modifier,
        )
    }
}

@Composable
internal fun SleepTimerContent(
    changeVisibility: (visible: Boolean) -> Unit,
    activeRemainingSeconds: Long?,
    defaultMinutes: Int,
    onStartTimer: (minutes: Int) -> Unit,
    onStartEndOfSong: () -> Unit,
    onCancelTimer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (activeRemainingSeconds != null) {
            val minutes = activeRemainingSeconds / 60
            val seconds = activeRemainingSeconds % 60

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "%d:%02d".format(minutes, seconds),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontFeatureSettings = "tnum",
                    ),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.sleep_timer_remaining),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ActionPill(
                icon = null,
                label = stringResource(R.string.cancel_timer),
                onClick = {
                    onCancelTimer()
                    changeVisibility(false)
                },
                filled = true,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            ActionPill(
                icon = null,
                label = stringResource(R.string.sleep_timer_end_of_song_btn),
                onClick = {
                    onStartEndOfSong()
                    changeVisibility(false)
                },
                filled = false,
                modifier = Modifier.fillMaxWidth(),
            )


                    var sliderValue by remember(defaultMinutes) {
                mutableIntStateOf(defaultMinutes.coerceIn(1, MAX_SLEEP_MINUTES))
            }

            var typed by remember(defaultMinutes) { mutableStateOf(sliderValue.toString()) }
            OutlinedTextField(
                value = typed,
                onValueChange = { text ->
                    typed = text.filter(Char::isDigit).take(MAX_SLEEP_DIGITS)
                    // What cannot run keeps the last value that can, rather than starting at nothing.
                    parseSleepMinutes(typed)?.let { sliderValue = it }
                },
                label = { Text(stringResource(R.string.sleep_timer_minutes_field)) },
                trailingIcon = {
                    Text(
                        stringResource(R.string.sleep_timer_minutes_short),
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = MaterialTheme.typography.titleMedium.copy(
                    fontFeatureSettings = "tnum",
                    textAlign = TextAlign.Center,
                ),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(120.dp),
            )


            val range =
                Constants.Ui.Player.SleepTimer.STEP_VALUE.toFloat()..(Constants.Ui.Player.SleepTimer.STEP_VALUE * Constants.Ui.Player.SleepTimer.STEP_AMOUNT).toFloat()
            val sliderStateDescription = stringResource(R.string.minutes, sliderValue)

            Slider(
                value = sliderValue.toFloat().coerceIn(range),
                onValueChange = { newValue ->
                    val rounded = newValue.roundToInt()
                    if (rounded != sliderValue) {
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                    }
                    sliderValue = rounded
                    typed = rounded.toString()
                },
                valueRange = range,
                steps = Constants.Ui.Player.SleepTimer.STEP_AMOUNT - 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        stateDescription = sliderStateDescription
                    },
            )

            val minLabel = Constants.Ui.Player.SleepTimer.STEP_VALUE
            val maxLabel =
                Constants.Ui.Player.SleepTimer.STEP_VALUE * Constants.Ui.Player.SleepTimer.STEP_AMOUNT

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.short_minutes, minLabel),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFeatureSettings = "tnum",
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.short_minutes, maxLabel),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFeatureSettings = "tnum",
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ActionPill(
                icon = null,
                label = stringResource(R.string.sleep_timer_start),
                onClick = {
                    onStartTimer(sliderValue)
                    changeVisibility(false)
                },
                filled = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private const val MAX_SLEEP_DIGITS = 4

/** A day. Past that a timer has stopped being a sleep timer, and is more likely a typing slip. */
private const val MAX_SLEEP_MINUTES = 24 * 60

/** The minutes a field's text asks for, or null when no timer could run on it. */
internal fun parseSleepMinutes(text: String): Int? =
    text.toIntOrNull()?.takeIf { it in 1..MAX_SLEEP_MINUTES }
