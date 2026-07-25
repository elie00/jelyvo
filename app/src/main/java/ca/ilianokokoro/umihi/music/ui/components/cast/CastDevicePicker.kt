package ca.ilianokokoro.umihi.music.ui.components.cast

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.mediarouter.media.MediaRouter
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.cast.CastManager

private fun MediaRouter.castRoutes(): List<MediaRouter.RouteInfo> =
    routes.filter {
        !it.isDefault &&
            it.isEnabled &&
            it.matchesSelector(CastManager.ROUTE_SELECTOR)
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
@OptIn(ExperimentalMaterial3Api::class)
fun CastDevicePickerSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val router = remember(context) { MediaRouter.getInstance(context) }
    val isCasting by CastManager.isCasting.collectAsStateWithLifecycle()
    var routes by remember { mutableStateOf(router.castRoutes()) }

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

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.cast_choose_device),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
            if (isCasting) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.cast_stop)) },
                    leadingContent = {
                        Icon(Icons.Rounded.StopCircle, contentDescription = null)
                    },
                    modifier = Modifier.clickable {
                        CastManager.endCurrentSession()
                        onDismiss()
                    },
                )
            }
            routes.forEach { route ->
                val description = route.description
                ListItem(
                    headlineContent = { Text(route.name) },
                    supportingContent = if (!description.isNullOrBlank()) {
                        { Text(description) }
                    } else {
                        null
                    },
                    leadingContent = {
                        Icon(
                            imageVector = if (route.isSelected) {
                                Icons.Rounded.CastConnected
                            } else {
                                Icons.Rounded.Cast
                            },
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable {
                        router.selectRoute(route)
                        onDismiss()
                    },
                )
            }
            if (routes.isEmpty() && !isCasting) {
                Text(
                    text = stringResource(R.string.cast_no_devices),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }
        }
    }
}
