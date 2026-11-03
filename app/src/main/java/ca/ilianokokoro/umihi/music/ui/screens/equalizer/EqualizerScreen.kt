@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package ca.ilianokokoro.umihi.music.ui.screens.equalizer

import android.app.Application
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import ca.ilianokokoro.umihi.music.ui.components.studio.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.managers.AudioOutputManager
import ca.ilianokokoro.umihi.music.ui.components.BackButton
import ca.ilianokokoro.umihi.music.ui.components.PageGutter
import ca.ilianokokoro.umihi.music.ui.components.PagePanel
import ca.ilianokokoro.umihi.music.ui.components.frosted
import ca.ilianokokoro.umihi.music.ui.components.frostedTopAppBarColors
import ca.ilianokokoro.umihi.music.ui.components.studio.PillSegmentedControl
import ca.ilianokokoro.umihi.music.ui.components.studio.Slider
import ca.ilianokokoro.umihi.music.ui.components.studio.Switch
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.math.roundToInt

@Composable
fun EqualizerScreen(
    onBack: () -> Unit,
    application: Application,
    equalizerViewModel: EqualizerViewModel = viewModel(
        factory = EqualizerViewModel.Factory(application)
    )
) {
    val state = equalizerViewModel.uiState.collectAsStateWithLifecycle().value

    val hazeState = remember { HazeState() }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                modifier = Modifier.frosted(hazeState),
                colors = frostedTopAppBarColors(),
                title = {
                    Text(
                        stringResource(R.string.equalizer_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = { BackButton(onBack = onBack) },
                actions = {
                    if (state.available) {
                        IconButton(onClick = equalizerViewModel::resetToDefault) {
                            Icon(
                                imageVector = Icons.Rounded.RestartAlt,
                                contentDescription = stringResource(R.string.equalizer_reset)
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .verticalScroll(rememberScrollState())
                .padding(top = paddingValues.calculateTopPadding())
                .padding(
                    start = PageGutter,
                    end = PageGutter,
                    bottom = Constants.Ui.SCROLLABLE_BOTTOM_PADDING
                ),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            if (!state.available) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.equalizer_unavailable),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                return@Column
            }

            // Active output the shown profile applies to
            Text(
                text = stringResource(
                    R.string.equalizer_active_output,
                    stringResource(
                        when (state.currentOutput) {
                            AudioOutputManager.Output.BLUETOOTH -> R.string.equalizer_output_bluetooth
                            AudioOutputManager.Output.WIRED -> R.string.equalizer_output_wired
                            AudioOutputManager.Output.SPEAKER -> R.string.equalizer_output_speaker
                        }
                    )
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )

            // Master enable switch
            PagePanel {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.equalizer_enable),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = state.enabled,
                        onCheckedChange = equalizerViewModel::setEnabled,
                    )
                }
            }

            // Presets
            if (state.presets.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.equalizer_presets),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    PillSegmentedControl(
                        options = state.presets,
                        selectedIndex = state.selectedPreset,
                        onSelect = equalizerViewModel::selectPreset,
                        enabled = state.enabled,
                    )
                    if (state.selectedPreset < 0) {
                        Text(
                                text = stringResource(R.string.equalizer_preset_custom),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // Bands
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.equalizer_bands),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    state.bands.forEach { band ->
                        BandColumn(
                            band = band,
                            min = state.minLevelMilliBel,
                            max = state.maxLevelMilliBel,
                            enabled = state.enabled,
                            onLevelChange = { level ->
                                equalizerViewModel.setBandLevel(band.index, level)
                            }
                        )
                    }
                }
            }

            // Bass boost
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.equalizer_bass_boost),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(
                            R.string.equalizer_bass_boost_value,
                            state.bassBoost / 10,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Slider(
                    value = state.bassBoost.toFloat(),
                    onValueChange = { equalizerViewModel.setBassBoost(it.roundToInt()) },
                    valueRange = 0f..1000f,
                    enabled = state.enabled
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BandColumn(
    band: BandState,
    min: Int,
    max: Int,
    enabled: Boolean,
    onLevelChange: (Int) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.width(64.dp)
    ) {
        Text(
            text = "${band.levelMilliBel / 100} dB",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box(
            modifier = Modifier
                .height(180.dp)
                .width(64.dp),
            contentAlignment = Alignment.Center
        ) {
            VerticalSlider(
                value = band.levelMilliBel.toFloat(),
                onValueChange = { onLevelChange(it.roundToInt()) },
                valueRange = min.toFloat()..max.toFloat(),
                enabled = enabled,
                modifier = Modifier.height(180.dp)
            )
        }
        Text(
            text = formatFrequency(band.centerFreqMilliHz),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * A [Slider] rotated 90° so it reads bottom (min) to top (max). The [modifier]
 * height controls the visible length of the track.
 */
@Composable
private fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        enabled = enabled,
        modifier = modifier
            .graphicsLayer {
                rotationZ = 270f
                transformOrigin = TransformOrigin(0f, 0f)
            }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(
                    Constraints(
                        minWidth = constraints.minHeight,
                        maxWidth = constraints.maxHeight,
                        minHeight = constraints.minWidth,
                        maxHeight = constraints.maxWidth
                    )
                )
                layout(placeable.height, placeable.width) {
                    placeable.place(-placeable.width, 0)
                }
            }
    )
}

private fun formatFrequency(milliHz: Int): String {
    val hz = milliHz / 1000
    return if (hz >= 1000) {
        val k = hz / 1000f
        if (k % 1f == 0f) "${k.toInt()} kHz" else "${(Math.round(k * 10) / 10f)} kHz"
    } else {
        "$hz Hz"
    }
}
