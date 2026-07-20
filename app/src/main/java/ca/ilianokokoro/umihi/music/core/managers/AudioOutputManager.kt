package ca.ilianokokoro.umihi.music.core.managers

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks the currently active audio *output* and classifies it into one of a
 * few coarse categories, so the equalizer can keep a distinct profile per
 * output (e.g. a different sound for Bluetooth headphones than the phone
 * speaker).
 *
 * The classification only cares about the broad kind of device, not the exact
 * model: [Output.BLUETOOTH] for any wireless route, [Output.WIRED] for wired /
 * USB headsets, and [Output.SPEAKER] as the built-in default. When several
 * outputs are connected the "most external" one wins (bluetooth > wired >
 * speaker), which matches where the audio actually routes.
 *
 * This is a singleton mirroring [EqualizerManager]; callers push a fresh
 * reading in via [refresh] (from an `AudioDeviceCallback` or on start-up).
 */
object AudioOutputManager {

    enum class Output { SPEAKER, WIRED, BLUETOOTH }

    private val _currentOutput = MutableStateFlow(Output.SPEAKER)
    val currentOutput: StateFlow<Output> = _currentOutput.asStateFlow()

    /** Recompute the active output from the system's current output devices. */
    fun refresh(context: Context) {
        val audioManager =
            context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val devices = runCatching {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        }.getOrNull() ?: return
        _currentOutput.value = classify(devices)
    }

    private fun classify(devices: Array<AudioDeviceInfo>): Output {
        val types = devices.map { it.type }
        if (types.any { it in BLUETOOTH_TYPES }) return Output.BLUETOOTH
        if (types.any { it in WIRED_TYPES }) return Output.WIRED
        return Output.SPEAKER
    }

    // BLE_HEADSET / BLE_SPEAKER are API 31+ constants; on older devices the
    // system simply never reports those types, so referencing them is safe.
    private val BLUETOOTH_TYPES = setOf(
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
    )

    private val WIRED_TYPES = setOf(
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_ACCESSORY,
    )
}
