package ca.ilianokokoro.umihi.music.ui.screens.equalizer

import androidx.compose.runtime.Immutable
import ca.ilianokokoro.umihi.music.core.managers.AudioOutputManager

/**
 * UI state for the equalizer screen.
 *
 * @param available       Whether a usable native equalizer is currently attached
 *                        (some devices have none, or no audio session is active yet).
 * @param enabled         Master enabled toggle.
 * @param bands           One entry per equalizer band.
 * @param minLevelMilliBel Lower bound of the band level range (millibel).
 * @param maxLevelMilliBel Upper bound of the band level range (millibel).
 * @param presets         Names of the device provided presets.
 * @param selectedPreset  Index into [presets], or -1 for a custom configuration.
 * @param bassBoost       Bass boost strength, 0..1000.
 * @param currentOutput   The active audio output the shown profile belongs to.
 */
@Immutable
data class EqualizerState(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val bands: List<BandState> = emptyList(),
    val minLevelMilliBel: Int = 0,
    val maxLevelMilliBel: Int = 0,
    val presets: List<String> = emptyList(),
    val selectedPreset: Int = -1,
    val bassBoost: Int = 0,
    val currentOutput: AudioOutputManager.Output = AudioOutputManager.Output.SPEAKER
)

/**
 * @param index            Band index in the native equalizer.
 * @param centerFreqMilliHz Center frequency in milli hertz (as reported by the effect).
 * @param levelMilliBel    Current gain of this band in millibel.
 */
@Immutable
data class BandState(
    val index: Int,
    val centerFreqMilliHz: Int,
    val levelMilliBel: Int
)
