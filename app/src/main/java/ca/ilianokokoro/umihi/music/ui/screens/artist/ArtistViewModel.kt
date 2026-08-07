package ca.ilianokokoro.umihi.music.ui.screens.artist

import android.app.Application
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.domain.CatalogResult
import ca.ilianokokoro.umihi.music.core.domain.MusicCatalog
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printe
import ca.ilianokokoro.umihi.music.core.youtube.YoutubeApiClient
import ca.ilianokokoro.umihi.music.data.catalog.YoutubeMusicCatalog
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository
import ca.ilianokokoro.umihi.music.models.adapters.toArtistPage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ArtistViewModel(
    application: Application,
    private val artistId: String,
    private val artistName: String,
) : AndroidViewModel(application) {
    private val datastoreRepository = DatastoreRepository(application)
    private val catalog: MusicCatalog = YoutubeMusicCatalog(
        settingsProvider = { datastoreRepository.getSettings() },
    )

    private val _uiState = MutableStateFlow<ArtistState>(ArtistState.Loading)
    val uiState = _uiState.asStateFlow()

    /** Follow state of the header. Seeded by the browse response, then optimistic. */
    private val _isSubscribed = MutableStateFlow(false)
    val isSubscribed = _isSubscribed.asStateFlow()

    /** Null while signed out or when the page exposes no subscribe button: hides the action. */
    private val _subscriptionChannelId = MutableStateFlow<String?>(null)
    val subscriptionChannelId = _subscriptionChannelId.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            catalog.artist(artistId, artistName).collect { result ->
                _uiState.update {
                    when (result) {
                        is CatalogResult.Loading -> ArtistState.Loading
                        is CatalogResult.Success -> {
                            val page = result.data.toArtistPage()
                            _subscriptionChannelId.value = page.artist.subscriptionChannelId
                            _isSubscribed.value = page.artist.subscribed == true
                            ArtistState.Success(page)
                        }
                        is CatalogResult.Error -> {
                            printe(message = "Failed to load artist", exception = result.exception)
                            ArtistState.Error(result.exception)
                        }
                    }
                }
            }
        }
    }

    /**
     * Follows/unfollows through the artist's channel. The button flips immediately and reverts
     * with a message when the call fails, mirroring the album library toggle.
     */
    fun toggleSubscribed() {
        val channelId = _subscriptionChannelId.value ?: return
        val target = !_isSubscribed.value
        _isSubscribed.value = target

        viewModelScope.launch {
            try {
                val settings = datastoreRepository.getSettings()
                YoutubeApiClient.setArtistSubscribed(channelId, target, settings)
                showToast(
                    if (target) R.string.artist_followed_toast else R.string.artist_unfollowed_toast
                )
            } catch (e: Exception) {
                printe(message = "Failed to update artist subscription", exception = e)
                _isSubscribed.value = !target
                showToast(R.string.artist_follow_failed_toast)
            }
        }
    }

    private fun showToast(@StringRes messageRes: Int) {
        val app = getApplication<Application>()
        Toast.makeText(app, app.getString(messageRes), Toast.LENGTH_SHORT).show()
    }

    companion object {
        fun Factory(
            application: Application,
            artistId: String,
            artistName: String,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ArtistViewModel(application, artistId, artistName)
            }
        }
    }
}
