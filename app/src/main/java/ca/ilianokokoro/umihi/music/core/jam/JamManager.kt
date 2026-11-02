package ca.ilianokokoro.umihi.music.core.jam

import android.os.SystemClock
import ca.ilianokokoro.umihi.music.core.UmihiHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException

/** Why a party could not be created or joined. */
enum class JamError { Unreachable, UnknownCode, PartyFull, Refused }

sealed interface JamStatus {
    data object Idle : JamStatus
    data object Connecting : JamStatus
    data class Failed(val error: JamError) : JamStatus
    data class InParty(val code: String, val members: List<PartyMember>) : JamStatus
}

/** Who this device claims to be. The server cannot verify it, and only tokens grant anything. */
data class JamIdentity(val deviceId: String, val displayName: String)

/**
 * The one connection to a party, shared by the settings screen that opens it and the playback
 * service that follows it. Server state is the truth: this class only carries it, and a dropped
 * socket is retried quietly because the server keeps a membership through short outages.
 */
object JamManager {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    private val JSON_TYPE = "application/json".toMediaType()
    private val PING_BURST_SPACING_MS = 250L
    private const val PING_BURST = 5
    private const val PING_INTERVAL_MS = 30_000L
    private val RECONNECT_DELAYS_MS = listOf(1_000L, 2_000L, 5_000L, 10_000L)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val socketClient by lazy {
        UmihiHttpClient.client.newBuilder().callTimeout(0, java.util.concurrent.TimeUnit.SECONDS).build()
    }

    val clock = ServerClock(SystemClock::elapsedRealtime)

    private val _status = MutableStateFlow<JamStatus>(JamStatus.Idle)
    val status: StateFlow<JamStatus> = _status.asStateFlow()

    private val _playback = MutableStateFlow<PartyPlayback?>(null)

    /** The party's latest state, or null outside a party. */
    val playback: StateFlow<PartyPlayback?> = _playback.asStateFlow()

    private class Connection(val serverUrl: String, val code: String, val token: String)

    @Volatile
    private var connection: Connection? = null

    @Volatile
    private var socket: WebSocket? = null
    private var pingJob: Job? = null
    private var reconnectAttempts = 0

    suspend fun create(serverUrl: String, identity: JamIdentity) =
        enter(serverUrl, "/api/parties", identity)

    suspend fun join(serverUrl: String, code: String, identity: JamIdentity) =
        enter(serverUrl, "/api/parties/${code.trim()}/join", identity)

    private suspend fun enter(serverUrl: String, path: String, identity: JamIdentity) {
        leave()
        _status.value = JamStatus.Connecting
        val body = buildJsonObject {
            put("userId", identity.deviceId)
            put("deviceId", identity.deviceId)
            put("displayName", identity.displayName)
        }.toString().toRequestBody(JSON_TYPE)
        val request = runCatching {
            Request.Builder().url((serverUrl + path).toHttpUrl()).post(body).build()
        }.getOrElse {
            _status.value = JamStatus.Failed(JamError.Refused)
            return
        }
        try {
            withContext(Dispatchers.IO) {
                UmihiHttpClient.client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        _status.value = JamStatus.Failed(
                            when (response.code) {
                                404 -> JamError.UnknownCode
                                409 -> JamError.PartyFull
                                else -> JamError.Refused
                            }
                        )
                        return@withContext
                    }
                    val reply = json.decodeFromString<JoinReply>(response.body?.string().orEmpty())
                    connection = Connection(serverUrl, reply.code, reply.token)
                    _playback.value = reply.party.playback
                    _status.value = JamStatus.InParty(reply.code, reply.party.members)
                    reconnectAttempts = 0
                    openSocket()
                }
            }
        } catch (_: IOException) {
            _status.value = JamStatus.Failed(JamError.Unreachable)
        } catch (_: kotlinx.serialization.SerializationException) {
            _status.value = JamStatus.Failed(JamError.Refused)
        }
    }

    /** Gives up the slot. Safe to call outside a party. */
    suspend fun leave() {
        val current = connection
        connection = null
        closeSocket()
        _playback.value = null
        _status.value = JamStatus.Idle
        if (current == null) return
        runCatching {
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url("${current.serverUrl}/api/parties/${current.code}/leave")
                    .header("Authorization", "Bearer ${current.token}")
                    .post(ByteArray(0).toRequestBody(JSON_TYPE))
                    .build()
                UmihiHttpClient.client.newCall(request).execute().close()
            }
        }
    }

    fun clearFailure() {
        if (_status.value is JamStatus.Failed) _status.value = JamStatus.Idle
    }

    fun sendPlay(positionMs: Long) = sendControl("play") { put("positionMs", positionMs) }

    fun sendPause(positionMs: Long) = sendControl("pause") { put("positionMs", positionMs) }

    fun sendSeek(positionMs: Long) = sendControl("seek") { put("positionMs", positionMs) }

    fun sendTrack(track: PartyTrack, isPlaying: Boolean) = send(
        buildJsonObject {
            put("type", "control")
            put("action", "setTrack")
            put("track", json.encodeToJsonElementOf(track))
            put("positionMs", 0L)
            put("isPlaying", isPlaying)
        }
    )

    private fun Json.encodeToJsonElementOf(track: PartyTrack) =
        parseToJsonElement(encodeToString(track))

    private inline fun sendControl(
        action: String,
        fields: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit,
    ) = send(
        buildJsonObject {
            put("type", "control")
            put("action", action)
            fields()
        }
    )

    private fun send(frame: JsonObject) {
        socket?.send(frame.toString())
    }

    private fun openSocket() {
        val current = connection ?: return
        val url = current.serverUrl.replaceFirst("https://", "wss://", ignoreCase = true)
        val request = Request.Builder()
            .url("$url/ws/parties/${current.code}")
            .header("Authorization", "Bearer ${current.token}")
            .build()
        socket = socketClient.newWebSocket(request, listener)
    }

    private fun closeSocket() {
        pingJob?.cancel()
        pingJob = null
        socket?.close(1000, null)
        socket = null
    }

    private fun sendPing() {
        send(
            buildJsonObject {
                put("type", "ping")
                put("clientMs", clock.nowMs())
            }
        )
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            reconnectAttempts = 0
            pingJob?.cancel()
            // A short burst converges on a good clock offset faster than a long, slow series.
            pingJob = scope.launch {
                repeat(PING_BURST) {
                    sendPing()
                    delay(PING_BURST_SPACING_MS)
                }
                while (true) {
                    delay(PING_INTERVAL_MS)
                    sendPing()
                }
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val frame = runCatching { json.decodeFromString<ServerFrame>(text) }.getOrNull() ?: return
            when (frame.type) {
                "pong" -> {
                    val sent = frame.clientMs ?: return
                    val server = frame.serverMs ?: return
                    clock.record(sent, server, clock.nowMs())
                }

                "welcome" -> {
                    frame.party?.let { party ->
                        applyPlayback(party.playback)
                        applyMembers(party.members)
                    }
                }

                "state" -> frame.playback?.let(::applyPlayback)

                "members" -> frame.members?.let(::applyMembers)

                "bye" -> scope.launch { leave() }
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (webSocket === socket) scheduleReconnect()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (webSocket === socket) scheduleReconnect()
        }
    }

    private fun applyPlayback(playback: PartyPlayback) {
        // Lower is stale. Equal is the heartbeat restating the truth, kept so drift gets corrected.
        val applied = _playback.value?.seq ?: Long.MIN_VALUE
        if (playback.seq >= applied) _playback.value = playback
    }

    private fun applyMembers(members: List<PartyMember>) {
        val current = _status.value as? JamStatus.InParty ?: return
        _status.value = current.copy(members = members)
    }

    private fun scheduleReconnect() {
        if (connection == null) return
        pingJob?.cancel()
        val wait = RECONNECT_DELAYS_MS[reconnectAttempts.coerceAtMost(RECONNECT_DELAYS_MS.lastIndex)]
        reconnectAttempts++
        scope.launch {
            delay(wait)
            if (connection != null) openSocket()
        }
    }
}
