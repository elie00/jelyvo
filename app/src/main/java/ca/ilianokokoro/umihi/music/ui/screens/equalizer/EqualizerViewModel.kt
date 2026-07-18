package ca.ilianokokoro.umihi.music.ui.screens.equalizer

import android.app.Application
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ca.ilianokokoro.umihi.music.core.managers.EqualizerManager
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository
import ca.ilianokokoro.umihi.music.data.repositories.dataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives [EqualizerManager] and exposes [EqualizerState].
 *
 * Persistence: the equalizer preferences are not part of the shared
 * [ca.ilianokokoro.umihi.music.models.UmihiSettings] object, so instead of
 * extending that model this view model stores its own keys directly through the
 * public `Context.dataStore` extension and [DatastoreRepository.save]. The
 * persisted config is pushed into [EqualizerManager] on init so it is
 * re-applied whenever a new audio session attaches.
 */
class EqualizerViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(EqualizerState())
    val uiState = _uiState.asStateFlow()

    private val datastoreRepository = DatastoreRepository(application)

    init {
        // 1. Load persisted config and seed the manager so it can re-apply it.
        viewModelScope.launch {
            val prefs = getApplication<Application>().dataStore.data.first()
            val enabled = prefs[KEY_ENABLED] ?: false
            val preset = prefs[KEY_PRESET] ?: CUSTOM_PRESET
            val bassBoost = prefs[KEY_BASS_BOOST] ?: 0
            val bands = decodeBands(prefs[KEY_BANDS])

            EqualizerManager.setDesiredConfig(
                enabled = enabled,
                preset = preset,
                bandLevels = bands,
                bassBoostStrength = bassBoost
            )
            refresh()
        }

        // 2. Rebuild the state whenever the manager's availability flips
        //    (e.g. playback starts and a real session is attached).
        viewModelScope.launch {
            EqualizerManager.isAvailable.collect { refresh() }
        }
    }

    fun setEnabled(enabled: Boolean) {
        EqualizerManager.setEnabled(enabled)
        viewModelScope.launch { datastoreRepository.save(KEY_ENABLED, enabled) }
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
        viewModelScope.launch { datastoreRepository.save(KEY_BASS_BOOST, strength.coerceIn(0, 1000)) }
        refresh()
    }

    private fun persistBandsAndPreset() {
        val encoded = encodeBands(EqualizerManager.getDesiredBandLevels())
        val preset = EqualizerManager.getSelectedPreset()
        viewModelScope.launch {
            datastoreRepository.save(KEY_BANDS, encoded)
            datastoreRepository.save(KEY_PRESET, preset)
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
        private const val CUSTOM_PRESET = -1

        private val KEY_ENABLED = booleanPreferencesKey("equalizer-enabled")
        private val KEY_PRESET = intPreferencesKey("equalizer-preset")
        private val KEY_BASS_BOOST = intPreferencesKey("equalizer-bass-boost")
        private val KEY_BANDS = stringPreferencesKey("equalizer-bands")

        /** Encodes band levels as "index:level;index:level". */
        private fun encodeBands(bands: Map<Short, Short>): String =
            bands.entries.joinToString(";") { "${it.key}:${it.value}" }

        private fun decodeBands(raw: String?): Map<Short, Short> {
            if (raw.isNullOrBlank()) return emptyMap()
            return raw.split(";").mapNotNull { entry ->
                val parts = entry.split(":")
                val band = parts.getOrNull(0)?.toShortOrNull()
                val level = parts.getOrNull(1)?.toShortOrNull()
                if (band != null && level != null) band to level else null
            }.toMap()
        }

        fun Factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                EqualizerViewModel(application)
            }
        }
    }
}
