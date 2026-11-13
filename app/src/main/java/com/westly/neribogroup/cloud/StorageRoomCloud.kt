package com.westly.neribogroup.cloud

import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** One line that another phone wrote to the Storage room's live feed (table storage_events). */
class RoomFeedEvent(
    val deviceId: String,
    val level: String,
    val source: String,
    val message: String,
    val appId: String
)

/**
 * The Storage room's live feed (backend/005_storage_room.sql). It does two small things:
 * - [send] adds one short text line to the table storage_events (through the RPC log_storage_event), so the owner's
 *   other phones can see the change at once;
 * - [realtime] gives a client that receives the lines other phones wrote.
 * The storage numbers themselves still travel in the normal sync documents. Nothing here is needed for the room to
 * work on one phone: with the cloud off, [send] does nothing and the realtime client waits quietly.
 */
class StorageRoomCloud(
    private val config: CloudConfig,
    private val api: SupabaseApi,
    /** True while company storage is over its total: like sync, the feed is stopped then. */
    private val blocked: () -> Boolean
) {

    /** Adds one line to the feed in the background. Best effort: a failure is ignored. */
    fun send(level: String, source: String, message: String, appId: String? = null) {
        if (config.cloudMode != CloudMode.ON || !config.signedIn) return
        if (blocked()) return
        val text = message.trim()
        if (text.isEmpty()) return
        val body = JSONObject()
            .put("p_device_id", config.deviceId)
            .put("p_level", level)
            .put("p_source", source.take(60))
            .put("p_message", text.take(500))
        if (appId != null && appId.isNotEmpty()) body.put("p_app_id", appId.take(100))
        Thread {
            try {
                api.rpc("log_storage_event", body)
            } catch (e: Exception) {
                // The feed is a convenience: the change itself is already saved on the phone.
            }
        }.start()
    }

    fun realtime(onState: (Boolean) -> Unit, onEvent: (RoomFeedEvent) -> Unit): StorageRealtime =
        StorageRealtime(config, api, onState, onEvent)
}

/**
 * Supabase Realtime over an OkHttp WebSocket, speaking the Phoenix channel protocol (version 1.0.0, JSON objects).
 * It listens to new rows of public.storage_events as the signed-in owner (row level security shows only the
 * owner's own rows). Lines written by this phone are skipped. It reconnects by itself and renews the token.
 * Call [start] and [stop] on the main thread; [onState] and [onEvent] are also called on the main thread.
 */
class StorageRealtime(
    private val config: CloudConfig,
    private val api: SupabaseApi,
    private val onState: (Boolean) -> Unit,
    private val onEvent: (RoomFeedEvent) -> Unit
) {

    private val handler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var socket: WebSocket? = null
    private var running = false
    private var joined = false
    private var attempt = 0
    private var ref = 0

    private val heartbeat = object : Runnable {
        override fun run() {
            val current = socket
            if (!running || current == null) return
            send(current, "phoenix", "heartbeat", JSONObject())
            renewTokenIfNeeded()
            handler.postDelayed(this, HEARTBEAT_MILLIS)
        }
    }

    private val reconnect = Runnable { connect() }

    fun start() {
        if (running) return
        running = true
        connect()
    }

    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
        val current = socket
        socket = null
        joined = false
        if (current != null) {
            try {
                current.close(1000, "closed")
            } catch (e: Exception) {
                // Nothing to do.
            }
        }
    }

    private fun connect() {
        if (!running) return
        if (config.cloudMode != CloudMode.ON || !config.signedIn ||
            config.projectUrl.isEmpty() || config.anonKey.isEmpty()
        ) {
            scheduleReconnect(IDLE_RETRY_MILLIS)
            return
        }
        Thread {
            // A token that is about to end is renewed first, away from the main thread.
            if (config.expiresAtMillis - System.currentTimeMillis() < 60_000L) {
                try {
                    api.refresh()
                } catch (e: Exception) {
                    // The connection attempt below reports the problem.
                }
            }
            handler.post { open() }
        }.start()
    }

    private fun open() {
        if (!running || socket != null) return
        if (!config.signedIn || config.accessToken.isEmpty()) {
            scheduleReconnect(IDLE_RETRY_MILLIS)
            return
        }
        val url = config.projectUrl.replaceFirst("https://", "wss://") +
            "/realtime/v1/websocket?apikey=" + URLEncoder.encode(config.anonKey, "UTF-8") + "&vsn=1.0.0"
        val request = try {
            Request.Builder().url(url).build()
        } catch (e: Exception) {
            scheduleReconnect(IDLE_RETRY_MILLIS)
            return
        }
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                handler.post { opened(webSocket) }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handler.post { received(webSocket, text) }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                handler.post { lost(webSocket) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                handler.post { lost(webSocket) }
            }
        })
    }

    private fun opened(webSocket: WebSocket) {
        if (webSocket !== socket) return
        val channelConfig = JSONObject()
            .put("broadcast", JSONObject().put("ack", false).put("self", false))
            .put("presence", JSONObject().put("key", ""))
            .put(
                "postgres_changes",
                JSONArray().put(
                    JSONObject().put("event", "INSERT").put("schema", "public").put("table", "storage_events")
                )
            )
        val payload = JSONObject().put("config", channelConfig).put("access_token", config.accessToken)
        send(webSocket, TOPIC, "phx_join", payload)
        handler.removeCallbacks(heartbeat)
        handler.postDelayed(heartbeat, HEARTBEAT_MILLIS)
    }

    private fun received(webSocket: WebSocket, text: String) {
        if (webSocket !== socket) return
        val message = try {
            JSONObject(text)
        } catch (e: Exception) {
            return
        }
        val event = message.optString("event")
        val topic = message.optString("topic")
        val payload = message.optJSONObject("payload") ?: JSONObject()
        if (event == "phx_reply" && topic == TOPIC) {
            if (payload.optString("status") == "ok") {
                if (!joined) {
                    joined = true
                    attempt = 0
                    onState(true)
                }
            } else {
                reset(webSocket)
            }
            return
        }
        if ((event == "phx_error" || event == "phx_close") && topic == TOPIC) {
            reset(webSocket)
            return
        }
        if (event != "postgres_changes") return
        val data = payload.optJSONObject("data") ?: return
        if (data.optString("type") != "INSERT") return
        val record = data.optJSONObject("record") ?: return
        val device = if (record.isNull("device_id")) "" else record.optString("device_id")
        if (device == config.deviceId) return
        val line = if (record.isNull("message")) "" else record.optString("message")
        if (line.isEmpty()) return
        onEvent(
            RoomFeedEvent(
                deviceId = device,
                level = if (record.isNull("level")) "INFO" else record.optString("level"),
                source = if (record.isNull("source")) "" else record.optString("source"),
                message = line,
                appId = if (record.isNull("app_id")) "" else record.optString("app_id")
            )
        )
    }

    private fun lost(webSocket: WebSocket) {
        if (webSocket !== socket) return
        reset(webSocket)
    }

    /** The connection is over (or refused): forget it and try again a little later. */
    private fun reset(webSocket: WebSocket) {
        socket = null
        handler.removeCallbacks(heartbeat)
        if (joined) {
            joined = false
            onState(false)
        }
        try {
            webSocket.cancel()
        } catch (e: Exception) {
            // Nothing to do.
        }
        attempt += 1
        val wait = minOf(30_000L, 2_000L shl minOf(attempt - 1, 4))
        scheduleReconnect(wait)
    }

    private fun scheduleReconnect(millis: Long) {
        if (!running) return
        handler.removeCallbacks(reconnect)
        handler.postDelayed(reconnect, millis)
    }

    private fun send(webSocket: WebSocket?, topic: String, event: String, payload: JSONObject) {
        if (webSocket == null) return
        ref += 1
        val frame = JSONObject()
            .put("topic", topic)
            .put("event", event)
            .put("payload", payload)
            .put("ref", ref.toString())
        try {
            webSocket.send(frame.toString())
        } catch (e: Exception) {
            // The listener reports a broken connection.
        }
    }

    /** The access token lasts about an hour. A new one is sent to the channel before the old one ends. */
    private fun renewTokenIfNeeded() {
        if (config.expiresAtMillis - System.currentTimeMillis() > 120_000L) return
        Thread {
            val result = try {
                api.refresh()
            } catch (e: Exception) {
                null
            }
            if (result == AuthResult.Ok) {
                handler.post {
                    if (running && joined) {
                        send(socket, TOPIC, "access_token", JSONObject().put("access_token", config.accessToken))
                    }
                }
            }
        }.start()
    }

    private companion object {
        const val TOPIC = "realtime:neribo-storage-room"
        const val HEARTBEAT_MILLIS = 25_000L
        const val IDLE_RETRY_MILLIS = 30_000L
    }
}
