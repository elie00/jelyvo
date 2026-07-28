package ca.ilianokokoro.umihi.music.core.managers

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
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
    enum class RouteKind { PHONE, BLUETOOTH, WIRED, USB, HDMI }

    data class AudioRoute(
        val id: Int,
        val name: String,
        val kind: RouteKind,
        val isActive: Boolean,
    )

    internal data class RouteCandidate(
        val id: Int,
        val name: String,
        val kind: RouteKind,
    )

    private val _currentOutput = MutableStateFlow(Output.SPEAKER)
    val currentOutput: StateFlow<Output> = _currentOutput.asStateFlow()
    private val _routes = MutableStateFlow<List<AudioRoute>>(emptyList())
    val routes: StateFlow<List<AudioRoute>> = _routes.asStateFlow()
    private val _activeRoute = MutableStateFlow<AudioRoute?>(null)
    val activeRoute: StateFlow<AudioRoute?> = _activeRoute.asStateFlow()

    /** Recompute the active output from the system's current output devices. */
    fun refresh(context: Context) {
        val audioManager =
            context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val devices = runCatching {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        }.getOrNull() ?: return
        val candidates = devices.mapNotNull(::toCandidate)
        val routedDeviceIds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                audioManager.getAudioDevicesForAttributes(MEDIA_AUDIO_ATTRIBUTES)
                    .map(AudioDeviceInfo::getId)
                    .toSet()
            }.getOrDefault(emptySet())
        } else {
            emptySet()
        }
        val active = selectActiveRoute(
            if (routedDeviceIds.isEmpty()) {
                candidates
            } else {
                candidates.filter { it.id in routedDeviceIds }
            }
        ) ?: selectActiveRoute(candidates)
        val routes = buildRoutes(candidates, active?.id)
        _routes.value = routes
        _activeRoute.value = routes.firstOrNull(AudioRoute::isActive)
        _currentOutput.value = when (active?.kind) {
            RouteKind.BLUETOOTH -> Output.BLUETOOTH
            RouteKind.WIRED, RouteKind.USB -> Output.WIRED
            RouteKind.PHONE, RouteKind.HDMI, null -> Output.SPEAKER
        }
    }

    private fun toCandidate(device: AudioDeviceInfo): RouteCandidate? {
        val kind = when (device.type) {
            in BLUETOOTH_TYPES -> RouteKind.BLUETOOTH
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> RouteKind.WIRED
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> RouteKind.USB
            AudioDeviceInfo.TYPE_HDMI,
            AudioDeviceInfo.TYPE_HDMI_ARC,
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_LINE_DIGITAL,
            AudioDeviceInfo.TYPE_AUX_LINE,
            AudioDeviceInfo.TYPE_BUS -> RouteKind.HDMI
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> RouteKind.PHONE
            else -> return null
        }
        return RouteCandidate(
            id = device.id,
            name = device.productName?.toString().orEmpty(),
            kind = kind,
        )
    }

    internal fun selectActiveRoute(routes: List<RouteCandidate>): RouteCandidate? =
        routes.maxByOrNull { routePriority(it.kind) }

    /**
     * Android may expose the same physical Bluetooth output through several transport profiles
     * (for example A2DP and BLE). Collapse those entries by visible name and kind, preferring the
     * routed profile so the single remaining row keeps its connected state.
     */
    internal fun buildRoutes(
        candidates: List<RouteCandidate>,
        activeId: Int?,
    ): List<AudioRoute> = candidates
        .groupBy { candidate ->
            candidate.kind to candidate.name.trim().lowercase()
        }
        .values
        .map { duplicates ->
            val activeCandidate = duplicates.firstOrNull { it.id == activeId }
            val representative = activeCandidate ?: duplicates.first()
            AudioRoute(
                id = representative.id,
                name = representative.name.trim(),
                kind = representative.kind,
                isActive = activeCandidate != null,
            )
        }
        .sortedByDescending { routePriority(it.kind) }

    private fun routePriority(kind: RouteKind): Int = when (kind) {
        RouteKind.BLUETOOTH -> 5
        RouteKind.USB -> 4
        RouteKind.WIRED -> 3
        RouteKind.HDMI -> 2
        RouteKind.PHONE -> 1
    }

    // BLE_HEADSET / BLE_SPEAKER are API 31+ constants; on older devices the
    // system simply never reports those types, so referencing them is safe.
    private val BLUETOOTH_TYPES = setOf(
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_BLE_BROADCAST,
    )

    private val MEDIA_AUDIO_ATTRIBUTES by lazy {
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    }
}
