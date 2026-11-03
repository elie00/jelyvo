package ca.ilianokokoro.umihi.music.core.managers

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Android names its presets in English, whatever the language. */
private val FRENCH_PRESET_NAMES = mapOf(
    "Classical" to "Classique",
    "Dance" to "Danse",
    "Flat" to "Plat",
    "Heavy Metal" to "Métal",
    "Hip Hop" to "Hip-hop",
)

/** Android's names can end in a NUL, which would hide them from the table and reach the screen. */
internal fun frenchPresetName(name: String): String {
    val clean = name.substringBefore('\u0000').trim()
    return FRENCH_PRESET_NAMES[clean] ?: clean
}

/**
 * Owns a native [Equalizer] (and [BassBoost]) bound to an audio session id.
 *
 * Design notes:
 *  - This manager has NO dependency on ExoPlayer. It only receives an
 *    `audioSessionId` through [attach]. The caller (PlaybackService) is
 *    responsible for providing the real session id.
 *  - The "desired" configuration (enabled flag, per band levels, bass boost
 *    strength, selected preset) is kept in memory and re-applied every time a
 *    new session is attached, so settings survive session changes (e.g. when a
 *    new track starts and ExoPlayer allocates a new audio session).
 *  - Every native call is wrapped in try/catch because some devices ship
 *    without a working equalizer effect. [isAvailable] reflects whether a
 *    usable [Equalizer] instance currently exists.
 *
 * The manager is an `object` (singleton) to mirror [PlayerManager]: a single
 * audio pipeline means a single equalizer.
 */
object EqualizerManager {

    private const val PRIORITY = 0
    private const val CUSTOM_PRESET = -1

    @Volatile
    private var equalizer: Equalizer? = null

    @Volatile
    private var bassBoost: BassBoost? = null

    @Volatile
    private var currentSessionId: Int = 0

    // ----- Desired (persisted / user chosen) configuration kept in memory -----
    private var desiredEnabled: Boolean = false
    private var desiredBassBoost: Int = 0 // 0..1000
    private var desiredPreset: Int = CUSTOM_PRESET
    private val desiredBandLevels = HashMap<Short, Short>() // band index -> millibel

    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    /**
     * Bind the effects to [audioSessionId]. Releases any previously attached
     * session first, then re-applies the desired configuration. Safe to call
     * repeatedly; it is a no-op when the same session is already attached.
     */
    @Synchronized
    fun attach(audioSessionId: Int) {
        if (audioSessionId == currentSessionId && equalizer != null) {
            return
        }

        releaseInternal()

        if (audioSessionId <= 0) {
            _isAvailable.value = false
            return
        }

        try {
            val eq = Equalizer(PRIORITY, audioSessionId)
            val bb = try {
                BassBoost(PRIORITY, audioSessionId)
            } catch (_: Throwable) {
                null
            }

            equalizer = eq
            bassBoost = bb
            currentSessionId = audioSessionId

            applyDesired()
            _isAvailable.value = true
        } catch (_: Throwable) {
            releaseInternal()
            _isAvailable.value = false
        }
    }

    /**
     * Seed the desired configuration (typically loaded from persistence) before
     * or after a session is attached. If a session is currently attached the
     * values are applied immediately.
     */
    @Synchronized
    fun setDesiredConfig(
        enabled: Boolean,
        preset: Int,
        bandLevels: Map<Short, Short>,
        bassBoostStrength: Int
    ) {
        desiredEnabled = enabled
        desiredPreset = preset
        desiredBassBoost = bassBoostStrength.coerceIn(0, 1000)
        desiredBandLevels.clear()
        desiredBandLevels.putAll(bandLevels)
        if (equalizer != null) {
            applyDesired()
        }
    }

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        desiredEnabled = enabled
        runCatching { equalizer?.enabled = enabled }
        runCatching { bassBoost?.enabled = enabled && desiredBassBoost > 0 }
    }

    fun isEnabled(): Boolean = desiredEnabled

    fun getBandCount(): Short = runCatching { equalizer?.numberOfBands ?: 0 }.getOrDefault(0)

    fun getBandLevelRange(): Pair<Short, Short> = runCatching {
        equalizer?.bandLevelRange?.let { it[0] to it[1] }
    }.getOrNull() ?: (0.toShort() to 0.toShort())

    /** Center frequency of [band] in milli hertz. */
    fun getCenterFreq(band: Int): Int =
        runCatching { equalizer?.getCenterFreq(band.toShort()) ?: 0 }.getOrDefault(0)

    fun getBandLevel(band: Int): Short = runCatching {
        equalizer?.getBandLevel(band.toShort())
    }.getOrNull() ?: desiredBandLevels[band.toShort()] ?: 0

    @Synchronized
    fun setBandLevel(band: Int, levelMillibel: Int) {
        val value = levelMillibel.toShort()
        desiredBandLevels[band.toShort()] = value
        // A manual band change breaks away from any preset.
        desiredPreset = CUSTOM_PRESET
        runCatching { equalizer?.setBandLevel(band.toShort(), value) }
    }

    fun getPresets(): List<String> = runCatching {
        val eq = equalizer ?: return emptyList()
        (0 until eq.numberOfPresets).map { frenchPresetName(eq.getPresetName(it.toShort())) }
    }.getOrDefault(emptyList())

    fun getSelectedPreset(): Int = desiredPreset

    /**
     * Apply the equalizer preset at [index] and capture the resulting band
     * levels into the desired configuration so they persist and can be
     * re-applied on the next session.
     */
    @Synchronized
    fun usePreset(index: Int) {
        val eq = equalizer ?: run {
            desiredPreset = index
            return
        }
        runCatching {
            eq.usePreset(index.toShort())
            desiredPreset = index
            for (band in 0 until eq.numberOfBands) {
                desiredBandLevels[band.toShort()] = eq.getBandLevel(band.toShort())
            }
        }
    }

    /** @param strength 0..1000 */
    @Synchronized
    fun setBassBoost(strength: Int) {
        desiredBassBoost = strength.coerceIn(0, 1000)
        runCatching {
            bassBoost?.let { bb ->
                if (bb.strengthSupported) {
                    bb.setStrength(desiredBassBoost.toShort())
                }
                bb.enabled = desiredEnabled && desiredBassBoost > 0
            }
        }
    }

    fun getBassBoost(): Int = desiredBassBoost

    /** Snapshot of the desired band levels (for persistence). */
    @Synchronized
    fun getDesiredBandLevels(): Map<Short, Short> = HashMap(desiredBandLevels)

    @Synchronized
    fun release() {
        releaseInternal()
        _isAvailable.value = false
    }

    private fun applyDesired() {
        val eq = equalizer ?: return
        runCatching { eq.enabled = desiredEnabled }
        val bandCount = runCatching { eq.numberOfBands.toInt() }.getOrDefault(0)
        desiredBandLevels.forEach { (band, level) ->
            if (band in 0 until bandCount) {
                runCatching { eq.setBandLevel(band, level) }
            }
        }
        bassBoost?.let { bb ->
            runCatching {
                if (bb.strengthSupported) {
                    bb.setStrength(desiredBassBoost.coerceIn(0, 1000).toShort())
                }
                bb.enabled = desiredEnabled && desiredBassBoost > 0
            }
        }
    }

    private fun releaseInternal() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        equalizer = null
        bassBoost = null
        currentSessionId = 0
    }
}
