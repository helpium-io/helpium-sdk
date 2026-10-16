package io.helpium.sdk

import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * A minimal client for the Pusher protocol Laravel Reverb speaks, over OkHttp,
 * so the SDK ships without the Pusher library. Reconnects with backoff and
 * resubscribes every channel. Callbacks arrive on the main thread.
 */
internal class PusherSocket(
    private val host: String,
    private val port: Int,
    private val useTls: Boolean,
    private val appKey: String,
    private val http: OkHttpClient,
    private val authorize: (socketId: String, channel: String) -> String,
) {
    var onEvent: ((channel: String, event: String, data: String) -> Unit)? = null
    var onConnected: (() -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val channels = linkedSetOf<String>()
    private var socket: WebSocket? = null
    private var socketId: String? = null
    private var attempts = 0
    private var stopped = true

    fun connect() {
        stopped = false
        open()
    }

    fun disconnect() {
        stopped = true
        socket?.close(1000, null)
        socket = null
        socketId = null
    }

    fun subscribe(channel: String) {
        if (channels.add(channel) && socketId != null) sendSubscribe(channel)
    }

    fun unsubscribe(channel: String) {
        if (channels.remove(channel)) send(JSONObject().put("event", "pusher:unsubscribe").put("data", JSONObject().put("channel", channel)))
    }

    /** A client event (whisper) such as `client-typing`. */
    fun trigger(channel: String, event: String, data: JSONObject) {
        if (socketId != null && channel in channels) send(JSONObject().put("event", event).put("channel", channel).put("data", data))
    }

    private fun open() {
        val scheme = if (useTls) "wss" else "ws"
        val url = "$scheme://$host:$port/app/$appKey?protocol=7&client=helpium-android&version=${BuildConfig.SDK_VERSION}&flash=false"
        socket = http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                main.post { if (webSocket === socket) handle(text) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                main.post { if (webSocket === socket) reconnect() }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                main.post { if (webSocket === socket) reconnect() }
            }
        })
    }

    private fun reconnect() {
        socket = null
        socketId = null
        if (stopped) return
        attempts++
        val delay = minOf(30_000L, 1000L shl minOf(attempts, 5))
        main.postDelayed({ if (!stopped && socket == null) open() }, delay)
    }

    private fun handle(text: String) {
        val frame = runCatching { JSONObject(text) }.getOrNull() ?: return
        val event = frame.optString("event")
        val data = when (val raw = frame.opt("data")) {
            is String -> raw
            is JSONObject -> raw.toString()
            else -> "{}"
        }

        when (event) {
            "pusher:connection_established" -> {
                socketId = runCatching { JSONObject(data).getString("socket_id") }.getOrNull()
                attempts = 0
                channels.forEach(::sendSubscribe)
                onConnected?.invoke()
            }
            "pusher:ping" -> send(JSONObject().put("event", "pusher:pong").put("data", JSONObject()))
            "pusher:error", "pusher:pong", "pusher_internal:subscription_succeeded" -> Unit
            else -> frame.optString("channel").takeIf { it.isNotEmpty() }?.let { onEvent?.invoke(it, event, data) }
        }
    }

    private fun sendSubscribe(channel: String) {
        val id = socketId ?: return
        if (!channel.startsWith("private-")) {
            send(JSONObject().put("event", "pusher:subscribe").put("data", JSONObject().put("channel", channel)))
            return
        }
        worker.execute {
            val auth = runCatching { authorize(id, channel) }.getOrNull() ?: return@execute
            main.post {
                send(JSONObject().put("event", "pusher:subscribe").put("data", JSONObject().put("channel", channel).put("auth", auth)))
            }
        }
    }

    private fun send(frame: JSONObject) {
        socket?.send(frame.toString())
    }
}
