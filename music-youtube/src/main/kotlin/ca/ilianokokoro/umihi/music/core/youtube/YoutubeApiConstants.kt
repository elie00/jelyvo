package ca.ilianokokoro.umihi.music.core.youtube

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * InnerTube endpoints, client descriptors and playback-URI markers.
 *
 * Extracted from `Constants.YoutubeApi` so the JVM clients (Android and macOS) share one
 * definition of the provider's wire contract.
 */
object YoutubeApiConstants {
    // Hosts mirror MainActivity's VIEW intent-filter: shared text from the mobile web app
    // carries m.youtube.com, which the www-only alternative silently dropped.
    const val URL_REGEX =
        """https?://(www\.|m\.)?(youtube\.com|youtu\.be|music\.youtube\.com)/\S+"""
    const val RETRY_COUNT = 3
    const val PODCAST_PLAYLIST_ID = "VLSE"
    const val RETRY_DELAY = 1000
    const val YOUTUBE_URL_PREFIX = "https://www.youtube.com/watch?v="
    // Playback URI marker: appended to a track's stream URI to request the music-video clip
    // stream (and its poster) instead of the default official-audio counterpart.
    const val FORCE_VIDEO_PARAM = "umihiForceVideo"
    // Lets the resolver skip the watch-next counterpart request for ordinary audio tracks.
    const val SOURCE_VIDEO_PARAM = "umihiSourceVideo"

    // Marks which half of a merged HD stream a URI stands for. Above 720p YouTube only ships
    // video-only tracks, so the player opens two sources and merges them.
    const val VIDEO_ONLY_PARAM = "umihiVideoOnly"
    const val AUDIO_TRACK_PARAM = "umihiAudioTrack"
    const val ORIGIN = "https://music.youtube.com"
    const val API_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
    const val USER_AGENT =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"

    object Browse {
        const val URL = "${ORIGIN}/youtubei/v1/browse?key=${API_KEY}&prettyPrint=false"
        const val PLAYLIST_BROWSE_ID = "FEmusic_liked_playlists"

        // Library (connected account collection) browse ids.
        // "LM" is the liked-songs playlist; the "VL" prefix matches the playlist convention.
        const val LIKED_SONGS_BROWSE_ID = "VLLM"
        const val LIKED_ALBUMS_BROWSE_ID = "FEmusic_liked_albums"
        const val LIBRARY_ARTISTS_BROWSE_ID = "FEmusic_library_corpus_artists"
        const val LIBRARY_LANDING_BROWSE_ID = "FEmusic_library_landing"
        const val MOODS_AND_GENRES_BROWSE_ID = "FEmusic_moods_and_genres"

        // Disabled as it was causing issues
//            object Fields {
//                const val PLAYLISTS =
//                    "contents.singleColumnBrowseResultsRenderer.tabs," +
//                    "contents.twoColumnBrowseResultsRenderer.tabs"
//
//                const val PLAYLISTS_CONTINUATION =
//                    "continuationContents.gridContinuation," +
//                    "continuationContents.musicLibraryContinuation"
//
//                const val SONGS =
//                    "contents.twoColumnBrowseResultsRenderer" +
//                        ".secondaryContents.sectionListRenderer.contents," +
//                    "contents.singleColumnBrowseResultsRenderer" +
//                        ".tabs.tabRenderer.content.sectionListRenderer.contents"
//
//                const val SONGS_CONTINUATION = "onResponseReceivedActions"
//            }
    }

    object Client {
        val WEB_REMIX =
            buildJsonObject {
                put("clientName", JsonPrimitive("WEB_REMIX"))
                put("clientVersion", JsonPrimitive("1.20250212.01.00"))
                put("userAgent", JsonPrimitive(USER_AGENT))
                put("xClientName", JsonPrimitive("67"))
            }

        val WEB_YOUTUBE = buildJsonObject {
            put("clientName", JsonPrimitive("WEB"))
            put("clientVersion", JsonPrimitive("2.20250212.01.00"))
            put("userAgent", JsonPrimitive(USER_AGENT))
            put("xClientName", JsonPrimitive("1"))
        }

        val ANDROID_VR = buildJsonObject {
            put("clientName", JsonPrimitive("ANDROID_VR"))
            put("clientVersion", JsonPrimitive("1.61.48"))
            put("clientId", JsonPrimitive("28"))

            put(
                "userAgent",
                JsonPrimitive(
                    "com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; Cronet/132.0.6808.3)"
                )
            )

            put("osName", JsonPrimitive("Android"))
            put("osVersion", JsonPrimitive("12"))
            put("deviceMake", JsonPrimitive("Oculus"))
            put("deviceModel", JsonPrimitive("Quest 3"))
            put("androidSdkVersion", JsonPrimitive("32"))
            put("buildId", JsonPrimitive("SQ3A.220605.009.A1"))
            put("cronetVersion", JsonPrimitive("132.0.6808.3"))
            put("packageName", JsonPrimitive("com.google.android.apps.youtube.vr.oculus"))
        }
    }

    object Create {
        const val URL = "${ORIGIN}/youtubei/v1/playlist/create?key=${API_KEY}&prettyPrint=false"
    }


    object Delete {
        const val URL = "${ORIGIN}/youtubei/v1/playlist/delete?key=${API_KEY}&prettyPrint=false"
    }

    object PlayerInfo {
        const val URL =
            "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"

//            object Fields {
//                const val SONG_INFO =
//                    "videoDetails.videoId,videoDetails.title,videoDetails.author,videoDetails.lengthSeconds," +
//                            "videoDetails.thumbnail.thumbnails,microformat.microformatDataRenderer.familySafe"
//
//                const val TRACKING =
//                    "playbackTracking.videostatsPlaybackUrl.baseUrl," +
//                            "playbackTracking.videostatsWatchtimeUrl.baseUrl"
//
//                const val STREAM =
//                    "responseContext.visitorData,playabilityStatus.status,playabilityStatus.reason," +
//                            "streamingData.adaptiveFormats.url,streamingData.adaptiveFormats.mimeType," +
//                            "streamingData.adaptiveFormats.bitrate"
//            }
    }

    object Next {
        const val URL = "${ORIGIN}/youtubei/v1/next?prettyPrint=false"
    }

    object Like {
        const val LIKE_URL = "${ORIGIN}/youtubei/v1/like/like?prettyPrint=false"
        const val REMOVE_LIKE_URL = "${ORIGIN}/youtubei/v1/like/removelike?prettyPrint=false"

        // Following an artist is a channel subscription: the target is the artist's channel
        // id ("UC…"), not a playlist like the album/like endpoints above.
        const val SUBSCRIBE_URL =
            "${ORIGIN}/youtubei/v1/subscription/subscribe?prettyPrint=false"
        const val UNSUBSCRIBE_URL =
            "${ORIGIN}/youtubei/v1/subscription/unsubscribe?prettyPrint=false"
    }

    object Search {
        const val URL = "https://music.youtube.com/youtubei/v1/search?prettyPrint=false"

        // YouTube proper, not YouTube Music: WEB_REMIX only indexes the music catalogue, so
        // the Videos tab could never surface anything beyond official clips.
        const val YOUTUBE_URL =
            "https://www.youtube.com/youtubei/v1/search?prettyPrint=false"
        const val SUGGESTIONS_URL =
            "https://music.youtube.com/youtubei/v1/music/get_search_suggestions?prettyPrint=false"
    }


}
