package ca.ilianokokoro.umihi.music.core.youtube

import ca.ilianokokoro.umihi.music.core.UmihiHttpClient
import ca.ilianokokoro.umihi.music.core.youtube.YoutubeAuthHelper.applyHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

object YoutubeApiClient {
    suspend fun browse(browseId: String, settings: YoutubeSession?, fields: String? = null): String {
        return requestWithContext(
            url = YoutubeApiConstants.Browse.URL,
            idName = "browseId",
            id = browseId,
            settings = settings,
            fields = fields,
        )
    }

    /** Browse with a `params` token, e.g. to open an artist's full "Songs" list. */
    suspend fun browse(browseId: String, params: String, settings: YoutubeSession): String {
        val base = YoutubeAuthHelper.buildContextBody("browseId", browseId, settings)
        val body = buildJsonObject {
            base.forEach { (key, value) -> put(key, value) }
            put("params", params)
        }
        return requestWithBody(
            url = YoutubeApiConstants.Browse.URL,
            body = body,
            settings = settings,
        )
    }

    suspend fun requestContinuation(
        continuationToken: String,
        settings: YoutubeSession,
        fields: String? = null,
    ): String {
        return requestWithContext(
            url = YoutubeApiConstants.Browse.URL,
            idName = "continuation",
            id = continuationToken,
            settings = settings,
            fields = fields,
        )
    }


    suspend fun createPlaylist(
        title: String,
        description: String,
        privacyStatus: String,
        videoIds: List<String> = listOf(),
        settings: YoutubeSession
    ): String {
        val baseBody = YoutubeAuthHelper.buildContextBody(
            idName = null,
            id = null,
            settings = settings
        )

        val body = buildJsonObject {
            baseBody.forEach { (key, value) ->
                put(key, value)
            }

            put("title", title)
            put("description", description)
            put("privacyStatus", privacyStatus)

            put(
                "videoIds",
                buildJsonArray {
                    videoIds.forEach { add(it) }
                }
            )
        }

        return requestWithBody(
            url = YoutubeApiConstants.Create.URL,
            body = body,
            settings = settings
        )
    }

    suspend fun deletePlaylist(
        playlistId: String,
        settings: YoutubeSession
    ): String {
        val baseBody = YoutubeAuthHelper.buildContextBody(
            idName = null,
            id = null,
            settings = settings
        )

        val strippedId = playlistId.removePrefix("VL")

        val body = buildJsonObject {
            baseBody.forEach { (key, value) ->
                put(key, value)
            }

            put("playlistId", strippedId)
        }


        return requestWithBody(
            url = YoutubeApiConstants.Delete.URL,
            body = body,
            settings = settings
        )
    }


    suspend fun getPlayerInfo(
        videoId: String,
        client: JsonObject? = null,
        visitorData: String? = null,
        settings: YoutubeSession? = null,
        fields: String? = null,
    ): String {
        return requestWithContext(
            url = YoutubeApiConstants.PlayerInfo.URL,
            idName = "videoId",
            id = videoId,
            settings = settings,
            client = client,
            visitorData = visitorData,
            fields = fields,
        )
    }

    /**
     * Watch-next endpoint: returns the play panel of a video, which links a music video to its
     * audio ("song") counterpart when both versions exist.
     */
    suspend fun next(videoId: String, playlistId: String? = null): String {
        val base = YoutubeAuthHelper.buildContextBody("videoId", videoId, settings = null)
        val body = buildJsonObject {
            base.forEach { (key, value) -> put(key, value) }
            if (playlistId != null) {
                put("playlistId", playlistId)
            }
            put("enablePersistentPlaylistPanel", true)
            put("isAudioOnly", true)
            put("tunerSettingValue", "AUTOMIX_SETTING_NORMAL")
        }
        return requestWithBody(
            url = YoutubeApiConstants.Next.URL,
            body = body,
        )
    }

    suspend fun setLike(
        videoId: String,
        liked: Boolean,
        settings: YoutubeSession
    ): String {
        val baseBody = YoutubeAuthHelper.buildContextBody(
            idName = null,
            id = null,
            settings = settings
        )

        val body = buildJsonObject {
            baseBody.forEach { (key, value) ->
                put(key, value)
            }

            put(
                "target",
                buildJsonObject {
                    put("videoId", videoId)
                }
            )
        }

        val url = if (liked) {
            YoutubeApiConstants.Like.LIKE_URL
        } else {
            YoutubeApiConstants.Like.REMOVE_LIKE_URL
        }

        return requestWithBody(
            url = url,
            body = body,
            settings = settings
        )
    }

    /**
     * Saves/removes an album from the account's library. Same like/removelike endpoints as
     * [setLike], but the target is the album's audio playlist ("OLAK5uy…") instead of a video —
     * which is how YT Music itself toggles the header's "Save to library" button.
     */
    /**
     * Browses a channel on YouTube itself. Used when YouTube Music has no artist page for it —
     * a plain channel is absent from the music catalogue but exists here.
     */
    suspend fun browseYoutubeChannel(channelId: String, settings: YoutubeSession): String {
        val base = YoutubeAuthHelper.buildContextBody(
            idName = "browseId",
            id = channelId,
            settings = settings,
            client = YoutubeApiConstants.Client.WEB_YOUTUBE,
        )
        val body = buildJsonObject {
            base.forEach { (key, value) -> put(key, value) }
            // The channel's Videos tab.
            put("params", JsonPrimitive("EgZ2aWRlb3PyBgQKAjoA"))
        }
        return requestWithBody(
            url = "https://www.youtube.com/youtubei/v1/browse?prettyPrint=false",
            body = body,
            settings = settings,
        )
    }

    /** Follows or unfollows an artist through its channel id. */
    suspend fun setArtistSubscribed(
        channelId: String,
        subscribed: Boolean,
        settings: YoutubeSession,
    ): String {
        val baseBody = YoutubeAuthHelper.buildContextBody(
            idName = null,
            id = null,
            settings = settings,
        )

        val body = buildJsonObject {
            baseBody.forEach { (key, value) -> put(key, value) }
            put("channelIds", buildJsonArray { add(JsonPrimitive(channelId)) })
        }

        return requestWithBody(
            url = if (subscribed) {
                YoutubeApiConstants.Like.SUBSCRIBE_URL
            } else {
                YoutubeApiConstants.Like.UNSUBSCRIBE_URL
            },
            body = body,
            settings = settings,
        )
    }

    suspend fun setAlbumSaved(
        playlistId: String,
        saved: Boolean,
        settings: YoutubeSession
    ): String {
        val baseBody = YoutubeAuthHelper.buildContextBody(
            idName = null,
            id = null,
            settings = settings
        )

        val body = buildJsonObject {
            baseBody.forEach { (key, value) ->
                put(key, value)
            }

            put(
                "target",
                buildJsonObject {
                    put("playlistId", playlistId)
                }
            )
        }

        val url = if (saved) {
            YoutubeApiConstants.Like.LIKE_URL
        } else {
            YoutubeApiConstants.Like.REMOVE_LIKE_URL
        }

        return requestWithBody(
            url = url,
            body = body,
            settings = settings
        )
    }

    /** [params] is the caller's scope token, e.g. a filter's params or a spell-correction link's. */
    suspend fun search(
        query: String,
        params: String,
    ): String {
        val base = YoutubeAuthHelper.buildContextBody(
            idName = "query",
            id = query,
            settings = null,
        )
        val body = buildJsonObject {
            base.forEach { (key, value) -> put(key, value) }
            if (params.isNotEmpty()) {
                put("params", params)
            }
        }
        return requestWithBody(
            url = YoutubeApiConstants.Search.URL,
            body = body,
        )
    }

    /**
     * Searches YouTube itself rather than YouTube Music. Used by the Videos tab so it reaches the
     * whole catalogue; [continuation] pages through it.
     */
    suspend fun searchYoutubeVideos(query: String, continuation: String? = null): String {
        val base = YoutubeAuthHelper.buildContextBody(
            idName = if (continuation == null) "query" else null,
            id = if (continuation == null) query else null,
            settings = null,
            client = YoutubeApiConstants.Client.WEB_YOUTUBE,
        )
        val body = buildJsonObject {
            base.forEach { (key, value) -> put(key, value) }
            if (continuation != null) {
                put("continuation", JsonPrimitive(continuation))
            } else {
                // Scope the search to videos, YouTube's own filter token.
                put("params", JsonPrimitive("EgIQAQ%3D%3D"))
            }
        }
        return requestWithBody(url = YoutubeApiConstants.Search.YOUTUBE_URL, body = body)
    }

    suspend fun searchSuggestions(input: String): String = requestWithContext(
        url = YoutubeApiConstants.Search.SUGGESTIONS_URL,
        idName = "input",
        id = input,
    )

    suspend fun searchContinuation(continuationToken: String): String {
        val url = YoutubeApiConstants.Search.URL.toHttpUrl().newBuilder()
            .addQueryParameter("continuation", continuationToken)
            .addQueryParameter("type", "next")
            .build()
            .toString()

        val body = YoutubeAuthHelper.buildContextBody(
            idName = null,
            id = null,
            settings = null
        )

        return requestWithBody(url = url, body = body)
    }

    private suspend fun requestWithBody(
        url: String,
        body: Any,
        settings: YoutubeSession? = null,
        client: JsonObject? = null,
        visitorData: String? = null,
        fields: String? = null,
    ): String = withContext(Dispatchers.IO) {
        val mediaType = "application/json; charset=utf-8".toMediaType()

        val requestBody = body
            .toString()
            .toRequestBody(mediaType)

        val httpUrl = if (fields != null) {
            url.toHttpUrl().newBuilder()
                .addQueryParameter("\$fields", fields)
                .build()
        } else {
            url.toHttpUrl()
        }

        val request = Request.Builder()
            .url(httpUrl)
            .post(requestBody)
            .applyHeaders(httpUrl, settings, visitorData, client)
            .build()

        UmihiHttpClient.client
            .newCall(request)
            .execute()
            .use { response ->
                if (!response.isSuccessful) {
                    val bodyPreview = response.body?.string()?.take(500) ?: "null"
                    throw IOException(
                        "HTTP ${response.code}: ${response.message} — body=$bodyPreview"
                    )
                }

                response.body?.string()
                    ?: throw IOException("Empty response body")
            }
    }

    private suspend fun requestWithContext(
        url: String,
        idName: String,
        id: String,
        settings: YoutubeSession? = null,
        client: JsonObject? = null,
        visitorData: String? = null,
        fields: String? = null,
    ): String {
        val body = YoutubeAuthHelper.buildContextBody(
            idName,
            id,
            settings,
            client,
            visitorData
        )

        return requestWithBody(
            url = url,
            body = body,
            settings = settings,
            client = client,
            visitorData = visitorData,
            fields = fields,
        )
    }
}
