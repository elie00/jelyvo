@file:OptIn(ExperimentalMaterial3Api::class)

package ca.ilianokokoro.umihi.music.ui.screens.auth

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.ui.components.BackButton
import ca.ilianokokoro.umihi.music.ui.navigation.viewmodels.SharedViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
fun AuthScreen(
    onBack: () -> Unit,
    application: Application,
    sharedViewModel: SharedViewModel,
    authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory(application))
) {
    val context = LocalContext.current
    val isDarkMode = isSystemInDarkTheme()

    LaunchedEffect(Unit) {
        authViewModel.eventFlow.collectLatest { event ->
            when (event) {
                AuthViewModel.ScreenEvent.Out.LoginCompleted -> {
                    Toast.makeText(context, R.string.login_success, Toast.LENGTH_SHORT).show()
                    sharedViewModel.requestPlaylistRefresh()
                    onBack()
                }
            }
        }
    }

    val webView = remember {
        val themedContext = ContextThemeWrapper(
            context,
            if (isDarkMode) {
                R.style.Theme_WebView_Dark
            } else {
                R.style.Theme_WebView_Light
            }
        )

        WebView(themedContext).apply {

            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true

                useWideViewPort = true
                loadWithOverviewMode = true

                setSupportZoom(false)
                displayZoomControls = false
            }

            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, true)
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean = handleAuthNavigation(context, view, request.url.toString())

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)

                    evaluateJavascript(
                        "window.yt?.config_?.DATASYNC_ID"
                    ) { result ->
                        authViewModel.onDataSyncIdFound(result)
                    }

                    authViewModel.onPageFinished(url)
                }
            }

            loadUrl(Constants.Auth.START_URL)
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = {
                Text(stringResource(R.string.log_in))
            },
            navigationIcon = {
                BackButton(onBack = onBack)
            }
        )
    }) { paddingValues ->
        Column(modifier = Modifier.padding(top = paddingValues.calculateTopPadding())) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize(),
                factory = { webView },
                onRelease = { view ->
                    view.stopLoading()
                    view.onPause()
                    view.destroy()
                }
            )
        }
    }
}

/**
 * Google sometimes redirects Android WebViews to an `intent://` Play Store URL when the YouTube
 * Music app is absent. Continue to the web destination so the login cookies can still be captured.
 */
internal fun authWebFallback(url: String): String? {
    val isExternalScheme = url.startsWith("intent:", ignoreCase = true) ||
        url.startsWith("market:", ignoreCase = true)
    return Constants.Auth.END_URL.takeIf {
        isExternalScheme && url.contains(YOUTUBE_MUSIC_PACKAGE, ignoreCase = true)
    }
}

private fun handleAuthNavigation(context: Context, view: WebView, url: String): Boolean {
    val scheme = Uri.parse(url).scheme?.lowercase()
    if (scheme == "http" || scheme == "https") {
        return false
    }

    authWebFallback(url)?.let { fallback ->
        view.loadUrl(fallback)
        return true
    }

    val externalIntent = runCatching {
        if (scheme == "intent") {
            Intent.parseUri(url, Intent.URI_INTENT_SCHEME).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                component = null
                selector = null
            }
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
        }
    }.getOrNull()

    val browserFallback = externalIntent
        ?.getStringExtra("browser_fallback_url")
        ?.takeIf { fallback ->
            val fallbackScheme = Uri.parse(fallback).scheme?.lowercase()
            fallbackScheme == "http" || fallbackScheme == "https"
        }

    when {
        externalIntent?.resolveActivity(context.packageManager) != null ->
            runCatching { context.startActivity(externalIntent) }

        browserFallback != null ->
            view.loadUrl(browserFallback)
    }

    // Never let WebView render its raw ERR_UNKNOWN_URL_SCHEME page.
    return true
}

private const val YOUTUBE_MUSIC_PACKAGE = "com.google.android.apps.youtube.music"
