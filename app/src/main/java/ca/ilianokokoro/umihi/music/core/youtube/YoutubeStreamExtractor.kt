package ca.ilianokokoro.umihi.music.core.youtube

import android.content.Context
import android.widget.Toast
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.PlaybackLoudnessStore
import ca.ilianokokoro.umihi.music.core.SingleFlight
import ca.ilianokokoro.umihi.music.core.YoutubeExtractor
import ca.ilianokokoro.umihi.music.core.cast.castContentType
import ca.ilianokokoro.umihi.music.core.diagnostics.FailureSource
import ca.ilianokokoro.umihi.music.core.diagnostics.PlaybackFailures
import ca.ilianokokoro.umihi.music.core.helpers.ConnectivityHelper
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printd
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printe
import ca.ilianokokoro.umihi.music.core.helpers.UmihiHelper.safeArray
import ca.ilianokokoro.umihi.music.core.helpers.UmihiHelper.safeObject
import ca.ilianokokoro.umihi.music.data.database.AppDatabase
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.AudioQuality
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.VideoQuality
import ca.ilianokokoro.umihi.music.models.Song
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.schabi.newpipe.extractor.ServiceList

private val visitorDataRef = AtomicReference<String?>(null)

/**
 * Thread-safe cache for the YouTube `visitorData` token, read and written by
 * concurrent stream resolutions (up to [Constants.Downloads.MAX_CONCURRENT_DOWNLOADS] at once).
 */
val visitorData: String?
    get() = visitorDataRef.get()

/** Resolves playable YouTube streams; the response parsing lives in [YoutubeDataExtractor]. */
object YoutubeStreamExtractor {
    /**
     * Session-only cache of resolved muxed (video) streams, keyed by youtubeId. Video URLs are
     * never persisted to the DB (the clip and audio variants share the same id), so this keeps a
     * pre-flight [probeVideoStream] check and the subsequent data-source resolution from paying
     * for two round-trips. In-memory only: cleared with the process.
     */
    private val videoStreamCache = ConcurrentHashMap<String, ResolvedAudioStream>()
    private val audioStreamCache = ConcurrentHashMap<String, ResolvedAudioStream>()
    private val streamResolutionSingleFlight = SingleFlight()

    /**
     * Pre-flight check for video mode: resolves [song]'s stream with the video preference and
     * reports whether an actual muxed (audio+video) stream came back — false when YouTube only
     * exposed audio for this id. The resolved URL is kept in [videoStreamCache] so the playback
     * data source reuses it instead of resolving again. Throws when resolution fails entirely.
     */
    suspend fun probeVideoStream(context: Context, song: Song): Boolean {
        val videoQuality = effectiveVideoQuality(context)
        val track = videoTrackFor(videoQuality)
        val key = videoCacheKey(song.youtubeId, track, videoQuality)

        videoStreamCache[key]?.let { cached ->
            if (isPlaybackUrlFresh(cached.url)) {
                return cached.isVideo
            }
            videoStreamCache.remove(key)
        }

        val resolved = getSongUrlFromYoutube(
            song = song,
            forceVideo = true,
            track = track,
            videoQuality = videoQuality,
        )
        videoStreamCache[key] = resolved
        return resolved.isVideo
    }

    /** Which half of the ladder a video request needs at [videoQuality]. */
    private fun videoTrackFor(videoQuality: VideoQuality): StreamTrack =
        if (VideoQualitySelector.needsSeparateAudio(videoQuality)) {
            StreamTrack.VideoOnly
        } else {
            StreamTrack.Muxed
        }

    /**
     * One id yields several different streams — the muxed one, the video-only rung at each
     * ceiling, and the separate audio track — so the cache has to tell them apart. Keying on the
     * id alone served the probe's muxed 720p URL to both halves of a merged HD item, which played
     * as an ordinary 720p stream with its AAC soundtrack.
     */
    private fun videoCacheKey(
        songId: String,
        track: StreamTrack,
        videoQuality: VideoQuality,
    ): String = "$songId:${track.name}:${videoQuality.name}"

    internal suspend fun getSongPlayerUrl(
        context: Context,
        song: Song,
        allowLocal: Boolean = false,
        track: StreamTrack = StreamTrack.Muxed,
    ): String = resolvePlayback(
        context = context,
        song = song,
        allowLocal = allowLocal,
        minRemainingSeconds = STREAM_EXPIRY_SAFETY_SECONDS,
        track = track,
    ).url

    /**
     * Resolves a receiver-accessible stream even when the same song has been downloaded locally.
     * Cast URLs use a wider freshness margin because queued MediaInfo objects may be consumed much
     * later than the current local item.
     */
    suspend fun getCastableStream(
        context: Context,
        song: Song,
        minRemainingSeconds: Long = CAST_URL_MIN_LIFETIME_SECONDS,
    ): CastableStream {
        val resolved = resolvePlayback(
            context = context,
            song = song,
            allowLocal = false,
            minRemainingSeconds = minRemainingSeconds,
        )
        return CastableStream(
            url = resolved.url,
            contentType = castContentType(resolved.mimeType, resolved.url),
        )
    }

    private suspend fun resolvePlayback(
        context: Context,
        song: Song,
        allowLocal: Boolean,
        minRemainingSeconds: Long,
        track: StreamTrack = StreamTrack.Muxed,
    ): ResolvedAudioStream {
        val videoQuality = effectiveVideoQuality(context)
        val quality = effectiveAudioQuality(context)
        invalidateStreamsIfQualityChanged(context, quality)

        val sessionCache = if (song.forceVideo) videoStreamCache else audioStreamCache
        val sessionKey = if (song.forceVideo) {
            videoCacheKey(song.youtubeId, track, videoQuality)
        } else {
            song.youtubeId
        }
        sessionCache[sessionKey]?.let { cached ->
            if (
                isPlaybackUrlFresh(
                    url = cached.url,
                    minRemainingSeconds = minRemainingSeconds,
                )
            ) {
                PlaybackLoudnessStore.update(song.youtubeId, cached.loudnessDb)
                printd("${song.youtubeId} : Got url from session cache")
                return cached
            }
            // A stricter Cast lookup may evict a URL local playback would still have reused. The
            // replacement is resolved and cached immediately with enough lifetime for the queue.
            sessionCache.remove(sessionKey)
        }

        val localSongRepository = AppDatabase.getInstance(context).songRepository()
        var savedSong: Song? = null
        try {
            savedSong = localSongRepository.getSong(song.youtubeId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (ex: Exception) {
            Toast.makeText(
                context,
                context.getString(R.string.local_song_repository_error),
                Toast.LENGTH_LONG,
            )
                .show()
            printe(ex.toString())
        }

        // The clip ("video version") stream and the default official-audio stream share the same
        // youtubeId, so the per-id cache is bypassed in video mode to avoid serving one for the other.
        if (savedSong != null && !song.forceVideo) {
            if (allowLocal && savedSong.audioFilePath != null) {
                PlaybackLoudnessStore.update(song.youtubeId, savedSong.loudnessDb)
                printd("${song.youtubeId} : Was downloaded")
                return ResolvedAudioStream(
                    url = savedSong.audioFilePath,
                    loudnessDb = savedSong.loudnessDb,
                )
            }

            if (savedSong.streamUrl != null) {
                if (
                    isPlaybackUrlFresh(
                        url = savedSong.streamUrl,
                        minRemainingSeconds = minRemainingSeconds,
                    )
                ) {
                    PlaybackLoudnessStore.update(song.youtubeId, savedSong.loudnessDb)
                    val cached = ResolvedAudioStream(
                        url = savedSong.streamUrl,
                        loudnessDb = savedSong.loudnessDb,
                    )
                    // Room's Song row does not persist the stream MIME type. Cast therefore derives
                    // it from the signed URL, then falls back to audio/mp4.
                    audioStreamCache[song.youtubeId] = cached
                    printd("${song.youtubeId} : Got url from saved")
                    return cached
                }
                printd("${song.youtubeId} : Saved url was invalid")
            }
        }

        val resolutionKey =
            "${song.youtubeId}:${song.forceVideo}:${quality.name}:${track.name}:${videoQuality.name}"
        return streamResolutionSingleFlight.execute(resolutionKey) {
            // Prefetch and an immediate user skip can arrive together. Only the owner resolves the
            // signed URL; every waiter receives the same stream instead of issuing a second player
            // request (and a second NewPipe fallback on failure).
            val resolved = getSongUrlFromYoutube(
                song = song,
                forceVideo = song.forceVideo,
                quality = quality,
                track = track,
                videoQuality = videoQuality,
            )
            if (!song.forceVideo) {
                audioStreamCache[song.youtubeId] = resolved
                localSongRepository.setStreamInfo(
                    songId = song.youtubeId,
                    streamUrl = resolved.url,
                    loudnessDb = resolved.loudnessDb,
                )
            } else {
                videoStreamCache[sessionKey] = resolved
            }
            PlaybackLoudnessStore.update(song.youtubeId, resolved.loudnessDb)
            printd("${song.youtubeId} : Got url from YouTube and saved song")
            resolved
        }
    }

    /** Data saver caps video the same way it caps audio: a metered network drops to 480p. */
    private suspend fun effectiveVideoQuality(context: Context): VideoQuality {
        val repository = DatastoreRepository(context.applicationContext)
        val preferred = repository.settings.first().videoQuality
        if (!repository.dataSaverOnMetered.first()) return preferred
        return if (ConnectivityHelper.isMeteredNetwork(context)) {
            VideoQuality.P480
        } else {
            preferred
        }
    }

    private suspend fun effectiveAudioQuality(context: Context): AudioQuality {
        val repository = DatastoreRepository(context.applicationContext)
        val preferred = repository.audioQuality.first()
        if (!repository.dataSaverOnMetered.first()) return preferred
        return if (ConnectivityHelper.isMeteredNetwork(context)) AudioQuality.Low else preferred
    }

    /**
     * Cached signed URLs are quality-specific but do not encode the selected preference in our
     * database. Flush exactly once when the effective setting changes.
     */
    private suspend fun invalidateStreamsIfQualityChanged(
        context: Context,
        quality: AudioQuality,
    ) {
        val repository = DatastoreRepository(context.applicationContext)
        if (repository.getLastStreamQuality() == quality) return

        audioStreamCache.clear()
        videoStreamCache.clear()
        AppDatabase.getInstance(context).songRepository().clearStreamUrls()
        repository.setLastStreamQuality(quality)
        printd("Audio quality changed to $quality: flushed cached stream urls")
    }

    private suspend fun getSongUrlFromYoutube(
        song: Song,
        retries: Int = YoutubeApiConstants.RETRY_COUNT,
        forceVideo: Boolean = false,
        quality: AudioQuality = AudioQuality.High,
        track: StreamTrack = StreamTrack.Muxed,
        videoQuality: VideoQuality = VideoQuality.P1080,
    ): ResolvedAudioStream {
        var lastError: Throwable? = null

        // Ordinary tracks already point at their official audio, so only video rows need the
        // watch-next counterpart lookup. This removes one full API RTT from almost every tap.
        val playbackSong = if (!forceVideo && song.isVideo) {
            resolvePreferredAudioSong(song)
        } else {
            song
        }

        val fastUrl = resolveAndroidVrStreamUrl(
            playbackSong.youtubeId,
            preferVideo = forceVideo,
            quality = quality,
            track = track,
            videoQuality = videoQuality,
        )

        if (fastUrl != null) {
            return fastUrl
        }

        printd("${song.youtubeId} : Falling back to NewPipe")
        repeat(retries) { attempt ->
            try {
                return withContext(Dispatchers.IO) {
                    resolveNewPipeStreamUrl(
                        playbackSong,
                        preferVideo = forceVideo,
                        quality = quality,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Throwable) {
                lastError = e

                printe(
                    "${song.youtubeId} : Failed attempt ${attempt + 1}/$retries with NewPipe: " +
                            "${e::class.simpleName}: ${e.message ?: "no message"}"
                )

                if (attempt < retries - 1) {
                    delay((YoutubeApiConstants.RETRY_DELAY * (attempt + 1)).milliseconds)
                } else {
                    // One entry for the whole round: its retries are the same failure.
                    PlaybackFailures.record(FailureSource.NewPipe, e::class.simpleName ?: "Unknown", e.message)
                }
            }
        }

        throw Exception(
            "${song.youtubeId} : Fatal fail. Could not get it after $retries attempts",
            lastError
        )
    }

    /**
     * Returns the song whose stream should actually be played: the ATV ("official audio")
     * counterpart when [song] is a music video that has one, otherwise [song] itself.
     * Any failure while looking up the counterpart silently falls back to the original song.
     */
    private suspend fun resolvePreferredAudioSong(song: Song): Song {
        val counterpart = try {
            YoutubeDataExtractor.extractAudioCounterpart(YoutubeApiClient.next(song.youtubeId))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            printe(
                "${song.youtubeId} : Audio counterpart lookup failed, using original: " +
                        "${e::class.simpleName}: ${e.message ?: "no message"}"
            )
            null
        } ?: return song

        printd("${song.youtubeId} : Playing audio counterpart ${counterpart.youtubeId}")
        return counterpart
    }

    private suspend fun resolveAndroidVrStreamUrl(
        videoId: String,
        retries: Int = YoutubeApiConstants.RETRY_COUNT,
        preferVideo: Boolean = false,
        quality: AudioQuality = AudioQuality.High,
        track: StreamTrack = StreamTrack.Muxed,
        videoQuality: VideoQuality = VideoQuality.P1080,
    ): ResolvedAudioStream? = withContext(Dispatchers.IO) {
        suspend fun executeRequest(): ResolvedAudioStream? {
            val response = YoutubeApiClient.getPlayerInfo(
                videoId = videoId,
                client = YoutubeApiConstants.Client.ANDROID_VR,
                visitorData = visitorData,
                //   fields = YoutubeApiConstants.PlayerInfo.Fields.STREAM,
            )

            return extractStreamFromAndroidVrResponse(
                response, preferVideo, quality, track, videoQuality,
            )
        }

        repeat(retries) { attempt ->
            val previousVisitorData = visitorData

            executeRequest()?.let {
                return@withContext it
            }

            val visitorDataUpdated =
                previousVisitorData != visitorData

            val isLastAttempt =
                attempt >= retries - 1

            if (!visitorDataUpdated || isLastAttempt) {
                return@repeat
            }

            printd(
                "Retrying ANDROID_VR with updated visitorData " +
                        "(${attempt + 1}/$retries)"
            )
        }

        null
    }

    private fun resolveNewPipeStreamUrl(
        song: Song,
        preferVideo: Boolean = false,
        quality: AudioQuality = AudioQuality.High,
    ): ResolvedAudioStream {
        YoutubeExtractor.ensureInitialized()
        val service = ServiceList.YouTube
        val extractor = service.getStreamExtractor(song.youtubeUrl)

        extractor.fetchPage()

        if (preferVideo) {
            // Muxed (audio+video) streams only: video-only streams would need a second audio URI
            // merged in, which the single-URI resolving data source cannot carry.
            val bestVideoStream = extractor.videoStreams
                .filter { it.content.isNotBlank() && !it.isVideoOnly }
                .maxByOrNull { stream ->
                    stream.resolution.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
                }

            if (bestVideoStream != null) {
                return ResolvedAudioStream(
                    url = bestVideoStream.content,
                    loudnessDb = null,
                    mimeType = bestVideoStream.format?.mimeType,
                    isVideo = true,
                )
            }
        }

        val bestAudioStream = selectAudioStream(
            streams = extractor.audioStreams.filter { it.content.isNotBlank() },
            quality = quality,
        ) { it.averageBitrate }
            ?: error("No valid audio streams found")

        return ResolvedAudioStream(
            url = bestAudioStream.content,
            loudnessDb = null,
            mimeType = bestAudioStream.format?.mimeType,
        )
    }

    private fun extractStreamFromAndroidVrResponse(
        text: String,
        preferVideo: Boolean = false,
        quality: AudioQuality = AudioQuality.High,
        track: StreamTrack = StreamTrack.Muxed,
        videoQuality: VideoQuality = VideoQuality.P1080,
    ): ResolvedAudioStream? {
        val root = Json.parseToJsonElement(text).jsonObject

        val newVisitorData = root["responseContext"]
            ?.safeObject()
            ?.get("visitorData")
            ?.jsonPrimitive
            ?.contentOrNull

        if (newVisitorData != null) {
            // First writer wins; concurrent resolutions never clobber each other.
            visitorDataRef.compareAndSet(null, newVisitorData)
        }

        val status = root["playabilityStatus"]
            ?.safeObject()
            ?.get("status")
            ?.jsonPrimitive
            ?.contentOrNull

        val reason = root["playabilityStatus"]
            ?.safeObject()
            ?.get("reason")
            ?.jsonPrimitive
            ?.contentOrNull

        // Video mode: prefer a muxed (progressive audio+video) entry from `streamingData.formats`
        // — a single URL playable by ProgressiveMediaSource, unlike video-only adaptive formats.
        // When none is exposed, fall through to the audio-only branch below (safe fallback).
        val streamingData = root["streamingData"]?.safeObject()

        val muxedFormat = when {
            track == StreamTrack.VideoOnly -> VideoQualitySelector.selectVideoOnly(
                adaptiveFormats = streamingData?.get("adaptiveFormats")?.safeArray(),
                quality = videoQuality,
            )

            preferVideo && track == StreamTrack.Muxed -> VideoQualitySelector.selectMuxed(
                formats = streamingData?.get("formats")?.safeArray(),
                quality = videoQuality,
            )

            else -> null
        }

        val audioFormat = selectAudioFormat(
            adaptiveFormats = root["streamingData"]
                ?.safeObject()
                ?.get("adaptiveFormats")
                ?.safeArray(),
            quality = quality,
        )

        val loudnessDb = root["playerConfig"]
            ?.safeObject()
            ?.get("audioConfig")
            ?.safeObject()
            ?.get("loudnessDb")
            ?.jsonPrimitive
            ?.doubleOrNull

        if (reason != null) {
            printe("ANDROID_VR Failed ($status). Reason : $reason ")
            PlaybackFailures.record(FailureSource.AndroidVr, status ?: "NO_STATUS", reason)
        }

        if (muxedFormat != null) {
            return ResolvedAudioStream(
                url = muxedFormat["url"]?.jsonPrimitive?.contentOrNull ?: return null,
                loudnessDb = loudnessDb,
                mimeType = muxedFormat["mimeType"]?.jsonPrimitive?.contentOrNull,
                isVideo = true,
            )
        }

        return audioFormat?.let {
            ResolvedAudioStream(
                url = it["url"]?.jsonPrimitive?.contentOrNull ?: return null,
                loudnessDb = loudnessDb,
                mimeType = it["mimeType"]?.jsonPrimitive?.contentOrNull,
            )
        }
    }

}

/**
 * Which track a caller wants out of a YouTube response.
 *
 * [Muxed] is the single progressive stream YouTube caps at 720p. Above that the ladder is split,
 * so an HD request asks for [VideoOnly] and [Audio] separately and merges them at playback.
 */
internal enum class StreamTrack { Muxed, VideoOnly, Audio }

private data class ResolvedAudioStream(
    val url: String,
    val loudnessDb: Double?,
    // Room currently persists the signed URL but not its format, so DB-cache hits leave this null.
    val mimeType: String? = null,
    // True when [url] is a muxed (audio+video) stream, false for an audio-only stream. Lets
    // callers detect that a video request silently fell back to audio.
    val isVideo: Boolean = false,
)

data class CastableStream(
    val url: String,
    val contentType: String,
)
