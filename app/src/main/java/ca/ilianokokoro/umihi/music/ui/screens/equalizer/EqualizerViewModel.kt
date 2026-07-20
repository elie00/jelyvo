package ca.ilianokokoro.umihi.music.ui.screens.equalizer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ca.ilianokokoro.umihi.music.core.managers.AudioOutputManager
import ca.ilianokokoro.umihi.music.core.managers.EqualizerManager
import ca.ilianokokoro.umihi.music.core.managers.EqualizerProfileStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives [EqualizerManager] and exposes [EqualizerState].
 *
 * Persistence: the equalizer preferences are stored per active audio output
 * (speaker / wired / bluetooth) through [EqualizerProfileStore]. The profile
 * for the current output is pushed into [EqualizerManager] so it is re-applied
 * whenever a new audio session attaches, and reloaded whenever the output
 * changes. Edits are saved into the profile of whichever output is active.
 */
class EqualizerViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(EqualizerState())
    val uiState = _uiState.asStateFlow()

    init {
        // 1. Follow the active output: seed the manager with that output's saved
        //    profile so it can re-apply it, and reload on every output change.
        AudioOutputManager.refresh(getApplication())
        viewModelScope.launch {
            AudioOutputManager.currentOutput.collect { output ->
                val profile = EqualizerProfileStore.load(getApplication(), output)
                EqualizerManager.setDesiredConfig(
                    enabled = profile.enabled,
                    preset = profile.preset,
                    bandLevels = profile.bands,
                    bassBoostStrength = profile.bassBoost
                )
                _uiState.update { it.copy(currentOutput = output) }
                refresh()
            }
        }

        // 2. Rebuild the state whenever the manager's availability flips
        //    (e.g. playback starts and a real session is attached).
        viewModelScope.launch {
            EqualizerManager.isAvailable.collect { refresh() }
        }
    }

    fun setEnabled(enabled: Boolean) {
        EqualizerManager.setEnabled(enabled)
        val output = AudioOutputManager.currentOutput.value
        viewModelScope.launch {
            EqualizerProfileStore.saveEnabled(getApplication(), output, enabled)
        }
        refresh()
    }

    fun selectPreset(index: Int) {
        EqualizerManager.usePreset(index)
        persistBandsAndPreset()
        refresh()
    }

    fun setBandLevel(band: Int, levelMilliBel: Int) {
        EqualizerManager.setBandLevel(band, levelMilliBel)
        persistBandsAndPreset()
        refresh()
    }

    fun setBassBoost(strength: Int) {
        EqualizerManager.setBassBoost(strength)
        val output = AudioOutputManager.currentOutput.value
        viewModelScope.launch {
            EqualizerProfileStore.saveBassBoost(getApplication(), output, strength.coerceIn(0, 1000))
        }
        refresh()
    }

    private fun persistBandsAndPreset() {
        val bands = EqualizerManager.getDesiredBandLevels()
        val preset = EqualizerManager.getSelectedPreset()
        val output = AudioOutputManager.currentOutput.value
        viewModelScope.launch {
            EqualizerProfileStore.saveBandsAndPreset(getApplication(), output, bands, preset)
        }
    }

    private fun refresh() {
        val available = EqualizerManager.isAvailable.value
        if (!available) {
            _uiState.update {
                it.copy(
                    available = false,
                    enabled = EqualizerManager.isEnabled(),
                    bassBoost = EqualizerManager.getBassBoost()
                )
            }
            return
        }

        val (min, max) = EqualizerManager.getBandLevelRange()
        val bandCount = EqualizerManager.getBandCount().toInt()
        val bands = (0 until bandCount).map { index ->
            BandState(
                index = index,
                centerFreqMilliHz = EqualizerManager.getCenterFreq(index),
                levelMilliBel = EqualizerManager.getBandLevel(index).toInt()
            )
        }

        _uiState.update {
            it.copy(
                available = true,
                enabled = EqualizerManager.isEnabled(),
                bands = bands,
                minLevelMilliBel = min.toInt(),
                maxLevelMilliBel = max.toInt(),
                presets = EqualizerManager.getPresets(),
                selectedPreset = EqualizerManager.getSelectedPreset(),
                bassBoost = EqualizerManager.getBassBoost()
            )
        }
    }

    companion object {
        fun Factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                EqualizerViewModel(application)
            }
        }
    }
}
