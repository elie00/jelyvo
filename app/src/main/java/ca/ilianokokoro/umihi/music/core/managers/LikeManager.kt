package ca.ilianokokoro.umihi.music.core.managers

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printe
import ca.ilianokokoro.umihi.music.core.youtube.YoutubeApiClient
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository
import ca.ilianokokoro.umihi.music.data.repositories.LibraryRepository
import ca.ilianokokoro.umihi.music.data.repositories.PlaylistRepository
import ca.ilianokokoro.umihi.music.models.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Single source of truth for like mutations made during the current app session.
 *
 * API rows may expose a like status, but not every YouTube surface does. Successful local
 * mutations therefore override embedded row metadata so the player, search results and playlists
 * agree immediately instead of waiting for a network refresh.
 */
object LikeManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    data class LikeChange(
        val song: Song,
        val isLiked: Boolean,
        val changedAtMs: Long = System.currentTimeMillis(),
    )

    private val _changes = MutableStateFlow<Map<String, LikeChange>>(emptyMap())
    val changes = _changes.asStateFlow()

    /** Clears account-scoped overrides when login cookies change or the user logs out. */
    fun reset() {
        _changes.value = emptyMap()
    }

    /** Whether the user is logged in; used to gate logged-in-only row actions. */
    fun isLoggedIn(context: Context): Flow<Boolean> {
        return DatastoreRepository(context.applicationContext).settings
            .map { !it.cookies.isEmpty() }
    }

    /** Resolves a row status, preferring a session mutation over contextual and API metadata. */
    fun isLiked(song: Song, contextualStatus: Boolean? = null): Boolean =
        resolveLikeStatus(
            sessionStatus = changes.value[song.youtubeId]?.isLiked,
            contextualStatus = contextualStatus,
            embeddedStatus = song.isLiked,
        )

    /**
     * Reconciles a server-provided liked-songs list with successful/optimistic session mutations.
     * New likes appear first and unliked tracks disappear immediately.
     */
    fun mergeLikedSongs(songs: List<Song>): List<Song> {
        val currentChanges = changes.value
        val retained = songs
            .filterNot { currentChanges[it.youtubeId]?.isLiked == false }
            .map { it.withLikeStatus(true) }
        val retainedIds = retained.asSequence().map(Song::youtubeId).toHashSet()
        val additions = currentChanges.values
            .asSequence()
            .filter { it.isLiked && it.song.youtubeId !in retainedIds }
            .sortedByDescending(LikeChange::changedAtMs)
            .map { it.song.withLikeStatus(true) }
            .toList()
        return additions + retained
    }

    /** Starts a row/menu mutation that survives the composable leaving the screen. */
    fun setLiked(
        song: Song,
        liked: Boolean,
        context: Context,
        currentStatus: Boolean? = null,
    ) {
        val appContext = context.applicationContext

        scope.launch {
            setLikedAndWait(
                song = song,
                liked = liked,
                context = appContext,
                currentStatus = currentStatus,
                showConfirmation = true,
            )
        }
    }

    /**
     * Suspended variant used by the player, which already owns its loading and rollback state.
     * Returns whether the server accepted the target state.
     */
    suspend fun setLikedAndWait(
        song: Song,
        liked: Boolean,
        context: Context,
        currentStatus: Boolean? = null,
        showConfirmation: Boolean = false,
    ): Boolean {
        val appContext = context.applicationContext
        val previousChange = changes.value[song.youtubeId]
        val previousStatus = currentStatus ?: isLiked(song)
        if (previousStatus == liked) {
            return true
        }

        val rollbackState = previousChange ?: LikeChange(
            song = song.withLikeStatus(previousStatus),
            isLiked = previousStatus,
        )
        recordChange(song, liked)
        val settings = DatastoreRepository(appContext).getSettings()
        if (settings.cookies.isEmpty()) {
            rollbackChange(song.youtubeId, attemptedStatus = liked, rollbackState)
            return false
        }

        return try {
            YoutubeApiClient.setLike(song.youtubeId, liked = liked, settings)
            LibraryRepository.updateLikedSnapshot(settings, song, liked)
            PlaylistRepository.updateLikedSnapshot(settings, song, liked)
            if (showConfirmation) {
                showToast(
                    appContext,
                    if (liked) {
                        R.string.added_to_liked_toast
                    } else {
                        R.string.removed_from_liked_toast
                    }
                )
            }
            true
        } catch (e: Exception) {
            rollbackChange(song.youtubeId, attemptedStatus = liked, rollbackState)
            printe(message = "Failed to update liked song: ${e.message}", exception = e)
            if (showConfirmation) {
                showToast(
                    appContext,
                    if (liked) {
                        R.string.add_to_liked_failed_toast
                    } else {
                        R.string.remove_from_liked_failed_toast
                    }
                )
            }
            false
        }
    }

    private fun recordChange(song: Song, liked: Boolean) {
        _changes.update { current ->
            current + (song.youtubeId to LikeChange(song.withLikeStatus(liked), liked))
        }
    }

    /**
     * Only rolls back if this request is still the latest mutation for the track, so a slower
     * failed request cannot overwrite a newer user action.
     */
    private fun rollbackChange(
        songId: String,
        attemptedStatus: Boolean,
        rollbackState: LikeChange,
    ) {
        _changes.update { current ->
            if (current[songId]?.isLiked != attemptedStatus) {
                current
            } else {
                current + (songId to rollbackState)
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

/** Pure precedence rule, kept separate so state recognition can be covered by local unit tests. */
internal fun resolveLikeStatus(
    sessionStatus: Boolean?,
    contextualStatus: Boolean?,
    embeddedStatus: Boolean?,
): Boolean = sessionStatus ?: contextualStatus ?: embeddedStatus ?: false
