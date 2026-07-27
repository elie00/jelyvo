package ca.ilianokokoro.umihi.music.ui.screens.auth

import android.app.Application
import android.webkit.CookieManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printe
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printd
import ca.ilianokokoro.umihi.music.core.helpers.UmihiHelper.isNullOrInvalidId
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository
import ca.ilianokokoro.umihi.music.models.Cookies
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(SettingsState())
    //  val uiState = _uiState.asStateFlow()

    private val _eventsChannel = MutableSharedFlow<ScreenEvent.Out>()
    val eventFlow = _eventsChannel.asSharedFlow()
    private val datastoreRepository = DatastoreRepository(application)

    fun onPageFinished(url: String?) {
        viewModelScope.launch {
            if (url?.contains(Constants.Auth.END_URL) == true && !_uiState.value.isLoggedIn) {
                val cookies = CookieManager.getInstance().getCookie(url).orEmpty()
                if (cookies.isBlank()) {
                    return@launch
                }

                // Claim this completion before the first suspension: WebView may report the final
                // URL more than once, and two completion events would pop two navigation entries.
                _uiState.update { it.copy(isLoggedIn = true) }

                // Do not navigate back until the session is actually persisted. Otherwise the
                // already-created HomeViewModel can read the previous logged-out state once more.
                try {
                    saveCookies(Cookies(cookies))
                    _eventsChannel.emit(ScreenEvent.Out.LoginCompleted)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    _uiState.update { it.copy(isLoggedIn = false) }
                    printe(message = "Failed to persist login cookies", exception = ex)
                }
            }
        }
    }

    fun onDataSyncIdFound(result: String) {
        viewModelScope.launch {
            result
                .trim('"')
                .substringBefore("||")
                .takeUnless { it.isNullOrInvalidId() }
                ?.let { datastoreRepository.saveDataSyncId(it) }
        }
    }

    private suspend fun saveCookies(cookies: Cookies) {
        // Never log the cookie value: it contains the user's YouTube session.
        printd("Got login cookies")
        datastoreRepository.saveCookies(cookies)
    }

    sealed interface ScreenEvent {
        sealed class Out {
            data object LoginCompleted : Out()
        }
    }

    companion object {
        fun Factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AuthViewModel(application)
            }
        }
    }

}
