package io.helpium.sdk

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** An error from the Helpium API (spec/API.md "Errors"). */
class HelpiumException(val status: Int, val code: String?, override val message: String?) : Exception(message)

/** The customer chat API, authenticated with the widget key. Blocking: call off the main thread. */
internal class Api(val baseUrl: String, val widgetKey: String, val http: OkHttpClient = defaultClient) {

    fun settings(locale: String) = WidgetSettings.parse(get("widget/settings", mapOf("lang" to locale)).getJSONObject("data"))

    fun conversation(token: String): Conversation? = get("conversations/$token").optJSONObject("data")?.let(Conversation::parse)

    fun createConversation(body: JSONObject) = Conversation.parse(post("conversations", body).getJSONObject("data"))

    fun sendMessage(token: String, body: JSONObject): List<ChatMessage> =
        post("conversations/$token/messages", body).optJSONArray("data").objects().map(ChatMessage::fromRest)

    fun messages(token: String): List<ChatMessage> = get("conversations/$token/messages").optJSONArray("data").objects().map(ChatMessage::fromRest)

    fun requestHuman(token: String) = Conversation.parse(post("conversations/$token/request-human").getJSONObject("data"))

    fun close(token: String) = Conversation.parse(post("conversations/$token/close").getJSONObject("data"))

    fun rate(token: String, rating: Int, comment: String?) {
        post("conversations/$token/rate", JSONObject().put("rating", rating).apply { if (!comment.isNullOrBlank()) put("comment", comment) })
    }

    fun feedback(token: String, messageId: Int, value: String) {
        post("conversations/$token/messages/$messageId/feedback", JSONObject().put("feedback", value))
    }

    fun retry(token: String, messageId: Int) {
        post("conversations/$token/messages/$messageId/retry")
    }

    fun updateStatus(token: String, status: String, ids: List<Int>) {
        post("conversations/$token/messages/status", JSONObject().put("status", status).put("message_ids", JSONArray(ids)))
    }

    fun identify(body: JSONObject): JSONObject = post("tracking/identify", body).getJSONObject("data")

    fun broadcastingAuth(socketId: String, channel: String): String =
        post("broadcasting/auth", JSONObject().put("socket_id", socketId).put("channel_name", channel)).getString("auth")

    fun registerDevice(body: JSONObject) {
        post("devices", body)
    }

    fun unregisterDevice(pushToken: String) {
        send("DELETE", "devices", JSONObject().put("push_token", pushToken))
    }

    private fun get(path: String, query: Map<String, String> = emptyMap()) = send("GET", path, null, query)

    private fun post(path: String, body: JSONObject? = null) = send("POST", path, body ?: JSONObject())

    private fun send(method: String, path: String, body: JSONObject?, query: Map<String, String> = emptyMap()): JSONObject {
        val url = "${baseUrl.trimEnd('/')}/api/customer/$path".toHttpUrl().newBuilder().apply {
            query.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()

        val request = Request.Builder()
            .url(url)
            .header("X-API-Key", widgetKey)
            .header("Accept", "application/json")
            .method(method, body?.toString()?.toRequestBody(JSON))
            .build()

        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val error = runCatching { JSONObject(text).optJSONObject("error") }.getOrNull()
                throw HelpiumException(response.code, error?.str("code"), error?.str("message"))
            }
            return if (text.isBlank()) JSONObject() else JSONObject(text)
        }
    }

    companion object {
        private val JSON = "application/json".toMediaType()

        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .build()
    }
}
