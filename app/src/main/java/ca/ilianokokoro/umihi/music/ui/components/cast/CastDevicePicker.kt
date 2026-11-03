package ca.ilianokokoro.umihi.music.ui.components.cast

import android.content.Context
import android.content.Intent
import android.media.MediaRouter2
import android.os.Build
import android.provider.Settings
import androidx.car.app.connection.CarConnection
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Observer
import androidx.mediarouter.media.MediaRouter
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.ui.components.studio.SheetHeading
import ca.ilianokokoro.umihi.music.ui.components.studio.SheetActionRow
import ca.ilianokokoro.umihi.music.ui.components.studio.ModalBottomSheet
import ca.ilianokokoro.umihi.music.ui.components.studio.AlertRule
import ca.ilianokokoro.umihi.music.core.cast.CastManager
import ca.ilianokokoro.umihi.music.core.managers.AudioOutputManager

private fun MediaRouter.castRoutes(): List<MediaRouter.RouteInfo> =
    routes.filter {
        !it.isDefault &&
            it.isEnabled &&
            it.matchesSelector(CastManager.ROUTE_SELECTOR)
    }

@Composable
fun rememberCarConnectionType(): Int {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val connection = remember(context) { CarConnection(context.applicationContext) }
    var connectionType by remember {
        mutableStateOf(CarConnection.CONNECTION_TYPE_NOT_CONNECTED)
    }
    DisposableEffect(connection, lifecycleOwner) {
        val observer = Observer<Int> { value ->
            connectionType = value ?: CarConnection.CONNECTION_TYPE_NOT_CONNECTED
        }
        connection.type.observe(lifecycleOwner, observer)
        onDispose { connection.type.removeObserver(observer) }
    }
    return connectionType
}

@Composable
fun rememberIsCastRouteAvailable(): Boolean {
    val context = LocalContext.current
    val router = remember(context) { MediaRouter.getInstance(context) }
    val castContext by CastManager.castContext.collectAsStateWithLifecycle()
    var available by remember { mutableStateOf(false) }

    DisposableEffect(router, castContext) {
        if (castContext == null) {
            available = false
            return@DisposableEffect onDispose {}
        }

        fun refresh() {
            available = router.castRoutes().isNotEmpty()
        }
        val callback = object : MediaRouter.Callback() {
            override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteSelected(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteUnselected(
                router: MediaRouter,
                route: MediaRouter.RouteInfo,
                reason: Int,
            ) = refresh()
        }
        refresh()
        router.addCallback(
            CastManager.ROUTE_SELECTOR,
            callback,
            MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY,
        )
        onDispose { router.removeCallback(callback) }
    }

    return available
}

@Composable
fun AvailableDevicesSheet(
    carConnectionType: Int,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val router = remember(context) { MediaRouter.getInstance(context) }
    val isCasting by CastManager.isCasting.collectAsStateWithLifecycle()
    val castDeviceName by CastManager.deviceName.collectAsStateWithLifecycle()
    val audioRoutes by AudioOutputManager.routes.collectAsStateWithLifecycle()
    val activeAudioRoute by AudioOutputManager.activeRoute.collectAsStateWithLifecycle()
    var routes by remember { mutableStateOf(router.castRoutes()) }

    LaunchedEffect(Unit) {
        AudioOutputManager.refresh(context.applicationContext)
    }

    DisposableEffect(router) {
        fun refresh() {
            routes = router.castRoutes()
        }
        val callback = object : MediaRouter.Callback() {
            override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteSelected(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteUnselected(
                router: MediaRouter,
                route: MediaRouter.RouteInfo,
                reason: Int,
            ) = refresh()
        }
        refresh()
        router.addCallback(
            CastManager.ROUTE_SELECTOR,
            callback,
            MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN,
        )
        onDispose { router.removeCallback(callback) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.available_devices),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            DeviceSectionLabel(stringResource(R.string.current_output))

            DeviceRow(
                icon = Icons.Rounded.PhoneAndroid,
                name = stringResource(R.string.this_phone),
                subtitle = if (!isCasting &&
                    activeAudioRoute?.kind == AudioOutputManager.RouteKind.PHONE &&
                    carConnectionType == CarConnection.CONNECTION_TYPE_NOT_CONNECTED
                ) {
                    stringResource(R.string.device_connected)
                } else {
                    stringResource(R.string.device_available)
                },
                selected = !isCasting &&
                    activeAudioRoute?.kind == AudioOutputManager.RouteKind.PHONE &&
                    carConnectionType == CarConnection.CONNECTION_TYPE_NOT_CONNECTED,
                onClick = {
                    if (isCasting) {
                        CastManager.endCurrentSession()
                        onDismiss()
                    } else {
                        openSystemOutputSwitcher(context)
                        onDismiss()
                    }
                },
            )

            if (carConnectionType != CarConnection.CONNECTION_TYPE_NOT_CONNECTED) {
                DeviceRow(
                    icon = Icons.Rounded.DirectionsCar,
                    name = stringResource(
                        if (carConnectionType == CarConnection.CONNECTION_TYPE_NATIVE) {
                            R.string.android_automotive
                        } else {
                            R.string.android_auto
                        }
                    ),
                    subtitle = stringResource(R.string.device_connected),
                    selected = !isCasting,
                    onClick = null,
                )
            }

            audioRoutes
                .filter { it.kind != AudioOutputManager.RouteKind.PHONE }
                .forEach { route ->
                    DeviceRow(
                        icon = route.kind.icon(),
                        name = route.name.ifBlank { stringResource(route.kind.fallbackName()) },
                        subtitle = stringResource(
                            if (route.isActive && !isCasting) {
                                R.string.device_connected
                            } else {
                                R.string.device_available
                            }
                        ),
                        selected = route.isActive && !isCasting,
                        onClick = {
                            openSystemOutputSwitcher(context)
                            onDismiss()
                        },
                    )
                }

            // Discovery is asynchronous: without a heading the whole section simply vanished,
            // leaving no clue whether the app was searching or had found nothing.
            DeviceSectionLabel(stringResource(R.string.cast_devices))

            if (routes.isEmpty() && !isCasting) {
                Text(
                    text = stringResource(R.string.cast_searching),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                )
            }

            routes.forEach { route ->
                val selected = route.isSelected ||
                    isCasting && route.name.toString() == castDeviceName
                DeviceRow(
                    icon = if (selected) Icons.Rounded.CastConnected else Icons.Rounded.Cast,
                    name = route.name,
                    subtitle = route.description?.takeIf(CharSequence::isNotBlank)?.toString()
                        ?: stringResource(
                            if (selected) R.string.device_connected
                            else R.string.device_available
                        ),
                    selected = selected,
                    onClick = {
                        router.selectRoute(route)
                        onDismiss()
                    },
                )
            }

            if (isCasting) {
                DeviceRow(
                    icon = Icons.Rounded.StopCircle,
                    name = stringResource(R.string.cast_stop),
                    subtitle = castDeviceName.orEmpty(),
                    selected = false,
                    onClick = {
                        CastManager.endCurrentSession()
                        onDismiss()
                    },
                )
            }

            AlertRule(Modifier.padding(vertical = 8.dp))
            DeviceRow(
                icon = Icons.Rounded.Settings,
                name = stringResource(R.string.manage_audio_outputs),
                subtitle = stringResource(R.string.manage_audio_outputs_description),
                selected = false,
                onClick = {
                    openSystemOutputSwitcher(context)
                    onDismiss()
                },
            )
        }
    }
}

@Composable
internal fun DeviceSectionLabel(text: String) {
    SheetHeading(text)
}

@Composable
internal fun DeviceRow(
    icon: ImageVector,
    name: String,
    subtitle: String,
    selected: Boolean,
    onClick: (() -> Unit)?,
) {
    SheetActionRow(
        icon = icon,
        label = name,
        description = subtitle,
        trailing = if (selected) {
            {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = stringResource(R.string.device_connected),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            null
        },
        onClick = onClick,
    )
}

private fun AudioOutputManager.RouteKind.icon(): ImageVector = when (this) {
    AudioOutputManager.RouteKind.PHONE -> Icons.Rounded.PhoneAndroid
    AudioOutputManager.RouteKind.BLUETOOTH -> Icons.Rounded.BluetoothAudio
    AudioOutputManager.RouteKind.WIRED -> Icons.Rounded.Headphones
    AudioOutputManager.RouteKind.USB -> Icons.Rounded.Usb
    AudioOutputManager.RouteKind.HDMI -> Icons.Rounded.Tv
}

private fun AudioOutputManager.RouteKind.fallbackName(): Int = when (this) {
    AudioOutputManager.RouteKind.PHONE -> R.string.this_phone
    AudioOutputManager.RouteKind.BLUETOOTH -> R.string.bluetooth_device
    AudioOutputManager.RouteKind.WIRED -> R.string.wired_audio_device
    AudioOutputManager.RouteKind.USB -> R.string.usb_audio_device
    AudioOutputManager.RouteKind.HDMI -> R.string.external_display
}

private fun openSystemOutputSwitcher(context: Context) {
    if (Build.VERSION.SDK_INT >= 34) {
        val shown = runCatching {
            MediaRouter2.getInstance(context).showSystemOutputSwitcher()
        }.getOrDefault(false)
        if (shown) return
    }
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
            )
        )
    }
}
