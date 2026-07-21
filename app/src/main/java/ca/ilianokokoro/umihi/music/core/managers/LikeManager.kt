package ca.ilianokokoro.umihi.music.core.managers

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printe
import ca.ilianokokoro.umihi.music.core.youtube.YoutubeApiClient
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository
import ca.ilianokokoro.umihi.music.models.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Row-level "add to liked songs": fires the same endpoint as the player's like toggle.
 * List rows don't carry a reliable like status (only the player's watch-next data does),
 * so the call is fire-and-confirm — [YoutubeApiClient.setLike] sets an absolute target
 * state, so liking an already liked song is a server-side no-op (YT Music behaves the same).
 */
object LikeManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Whether the user is logged in; used to gate logged-in-only row actions. */
    fun isLoggedIn(context: Context): Flow<Boolean> {
        return DatastoreRepository(context.applicationContext).settings
            .map { !it.cookies.isEmpty() }
    }

    fun addToLiked(song: Song, context: Context) {
        val appContext = context.applicationContext

        scope.launch {
            val settings = DatastoreRepository(appContext).getSettings()
            if (settings.cookies.isEmpty()) {
                return@launch
            }

            try {
                YoutubeApiClient.setLike(song.youtubeId, liked = true, settings)
                showToast(appContext, R.string.added_to_liked_toast)
            } catch (e: Exception) {
                printe(message = "Failed to add song to liked songs: ${e.message}", exception = e)
                showToast(appContext, R.string.add_to_liked_failed_toast)
            }
        }
    }

    private suspend fun showToast(context: Context, @StringRes messageRes: Int) {
        withContext(Dispatchers.Main) {
            Toast.makeText(
                context,
                context.getString(messageRes),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
