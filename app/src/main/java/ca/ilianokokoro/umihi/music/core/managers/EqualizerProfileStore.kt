package ca.ilianokokoro.umihi.music.core.managers

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import ca.ilianokokoro.umihi.music.core.managers.AudioOutputManager.Output
import ca.ilianokokoro.umihi.music.data.repositories.dataStore
import kotlinx.coroutines.flow.first

/**
 * Persists a distinct equalizer [Profile] for each [Output] category in
 * DataStore. Keys are suffixed per output (e.g. `eq_preset_bluetooth`), so
 * moving from the phone speaker to Bluetooth headphones can automatically load
 * a different sound.
 *
 * Migration: earlier builds kept a single global equalizer config under the
 * `equalizer-*` keys. On first read those legacy values seed the `speaker`
 * profile, so an existing user keeps their sound as the default output profile.
 */
object EqualizerProfileStore {

    private const val CUSTOM_PRESET = -1

    data class Profile(
        val enabled: Boolean = false,
        val preset: Int = CUSTOM_PRESET,
        val bands: Map<Short, Short> = emptyMap(),
        val bassBoost: Int = 0,
    )

    // Legacy (pre per-output) global keys, migrated into the speaker profile.
    private val LEGACY_ENABLED = booleanPreferencesKey("equalizer-enabled")
    private val LEGACY_PRESET = intPreferencesKey("equalizer-preset")
    private val LEGACY_BASS = intPreferencesKey("equalizer-bass-boost")
    private val LEGACY_BANDS = stringPreferencesKey("equalizer-bands")

    private fun suffix(output: Output) = when (output) {
        Output.BLUETOOTH -> "bluetooth"
        Output.WIRED -> "wired"
        Output.SPEAKER -> "speaker"
    }

    private fun enabledKey(output: Output) = booleanPreferencesKey("eq_enabled_${suffix(output)}")
    private fun presetKey(output: Output) = intPreferencesKey("eq_preset_${suffix(output)}")
    private fun bassKey(output: Output) = intPreferencesKey("eq_bass_${suffix(output)}")
    private fun bandsKey(output: Output) = stringPreferencesKey("eq_bands_${suffix(output)}")

    suspend fun load(context: Context, output: Output): Profile {
        if (output == Output.SPEAKER) {
            migrateLegacyToSpeaker(context)
        }
        val prefs = context.dataStore.data.first()
        return Profile(
            enabled = prefs[enabledKey(output)] ?: false,
            preset = prefs[presetKey(output)] ?: CUSTOM_PRESET,
            bands = decodeBands(prefs[bandsKey(output)]),
            bassBoost = prefs[bassKey(output)] ?: 0,
        )
    }

    suspend fun saveEnabled(context: Context, output: Output, enabled: Boolean) {
        context.dataStore.edit { it[enabledKey(output)] = enabled }
    }

    suspend fun saveBandsAndPreset(
        context: Context,
        output: Output,
        bands: Map<Short, Short>,
        preset: Int,
    ) {
        context.dataStore.edit {
            it[bandsKey(output)] = encodeBands(bands)
            it[presetKey(output)] = preset
        }
    }

    suspend fun saveBassBoost(context: Context, output: Output, strength: Int) {
        context.dataStore.edit { it[bassKey(output)] = strength.coerceIn(0, 1000) }
    }

    /**
     * Idempotently copies the legacy global config into the speaker profile the
     * first time it is needed (only when the speaker profile has not been
     * written yet and legacy values exist).
     */
    private suspend fun migrateLegacyToSpeaker(context: Context) {
        val prefs = context.dataStore.data.first()
        val alreadyMigrated = prefs[enabledKey(Output.SPEAKER)] != null ||
            prefs[bandsKey(Output.SPEAKER)] != null ||
            prefs[presetKey(Output.SPEAKER)] != null ||
            prefs[bassKey(Output.SPEAKER)] != null
        if (alreadyMigrated) return

        val hasLegacy = prefs[LEGACY_ENABLED] != null ||
            prefs[LEGACY_PRESET] != null ||
            prefs[LEGACY_BANDS] != null ||
            prefs[LEGACY_BASS] != null
        if (!hasLegacy) return

        context.dataStore.edit {
            it[enabledKey(Output.SPEAKER)] = prefs[LEGACY_ENABLED] ?: false
            it[presetKey(Output.SPEAKER)] = prefs[LEGACY_PRESET] ?: CUSTOM_PRESET
            it[bandsKey(Output.SPEAKER)] = prefs[LEGACY_BANDS] ?: ""
            it[bassKey(Output.SPEAKER)] = prefs[LEGACY_BASS] ?: 0
        }
    }

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
}
