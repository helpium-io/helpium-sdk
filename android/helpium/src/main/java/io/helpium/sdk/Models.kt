package io.helpium.sdk

import org.json.JSONArray
import org.json.JSONObject

internal fun JSONObject.str(key: String): String? = if (isNull(key) || !has(key)) null else optString(key)

internal data class WidgetSettings(
    val enableChat: Boolean,
    val enableBot: Boolean,
    val requireCustomerDetails: Boolean,
    val collectMarketingOptIn: Boolean,
    val humanHandoffMode: String,
    val aiReplyMode: String,
    val botName: String,
    val botAvatarUrl: String?,
    val primaryColor: String,
    val isOnline: Boolean,
    val offlineMessage: String?,
    val conversationRatingsEnabled: Boolean,
    val aiAvailable: Boolean,
    val websocket: Websocket,
    val instantAnswers: List<InstantAnswer>,
) {
    data class Websocket(val host: String, val port: Int, val scheme: String, val appKey: String, val useApiHost: Boolean)
    data class InstantAnswer(val id: Int, val label: String, val isShortcut: Boolean)

    companion object {
        fun parse(json: JSONObject): WidgetSettings {
            val ws = json.getJSONObject("websocket")
            val answers = json.optJSONArray("instant_answers") ?: JSONArray()
            return WidgetSettings(
                enableChat = json.optBoolean("enable_chat", true),
                enableBot = json.optBoolean("enable_bot", true),
                requireCustomerDetails = json.optBoolean("require_customer_details", true),
                collectMarketingOptIn = json.optBoolean("collect_marketing_opt_in", false),
                humanHandoffMode = json.optString("human_handoff_mode", "on_request"),
                aiReplyMode = json.optString("ai_reply_mode", "auto"),
                botName = json.optString("bot_name", "Helpium Bot"),
                botAvatarUrl = json.str("bot_avatar_url"),
                primaryColor = json.optString("primary_color", "#6366f1"),
                isOnline = json.optBoolean("is_online", true),
                offlineMessage = json.str("offline_message"),
                conversationRatingsEnabled = json.optBoolean("conversation_ratings_enabled", false),
                aiAvailable = json.optBoolean("ai_available", true),
                websocket = Websocket(
                    host = ws.getString("host"),
                    port = ws.optInt("port", 443),
                    scheme = ws.optString("scheme", "https"),
                    appKey = ws.getString("app_key"),
                    useApiHost = ws.optBoolean("use_api_host", false),
                ),
                instantAnswers = (0 until answers.length()).map {
                    val a = answers.getJSONObject(it)
                    InstantAnswer(a.getInt("id"), a.getString("label"), a.optBoolean("is_shortcut", false))
                },
            )
        }
    }
}

internal data class Agent(val id: Int, val name: String, val avatarUrl: String?) {
    companion object {
        fun parse(json: JSONObject?): Agent? = json?.let { Agent(it.getInt("id"), it.optString("name"), it.str("avatar_url")) }
    }
}

internal data class Conversation(
    val id: Int,
    val status: String,
    val assignedAgent: Agent?,
    val closedReason: String?,
    val rated: Boolean,
    val customerIdentified: Boolean,
    val messages: List<ChatMessage>?,
    val widgetToken: String?,
) {
    companion object {
        fun parse(json: JSONObject): Conversation {
            val list = json.optJSONArray("messages")
            return Conversation(
                id = json.getInt("id"),
                status = json.getString("status"),
                assignedAgent = Agent.parse(json.optJSONObject("assigned_agent")),
                closedReason = json.str("closed_reason"),
                rated = json.optBoolean("rated", false),
                customerIdentified = json.optBoolean("customer_identified", false),
                messages = list?.let { arr -> (0 until arr.length()).map { ChatMessage.fromRest(arr.getJSONObject(it)) } },
                widgetToken = json.str("widget_token"),
            )
        }
    }
}

internal enum class Role { CUSTOMER, BOT, AGENT, SYSTEM;

    val isFromTeam get() = this == BOT || this == AGENT

    companion object {
        /** REST says `user` for the customer and `agent` for a human. */
        fun fromRest(value: String) = when (value) {
            "user" -> CUSTOMER
            "assistant" -> BOT
            "system" -> SYSTEM
            else -> AGENT
        }

        /** The socket says `customer`, and `user` for a human agent. */
        fun fromSenderType(value: String) = when (value) {
            "customer" -> CUSTOMER
            "bot" -> BOT
            "system" -> SYSTEM
            else -> AGENT
        }
    }
}

internal data class Attachment(val name: String?, val url: String, val type: String?) {
    val isImage get() = type?.startsWith("image/") == true
}

internal data class Source(val title: String, val url: String?)

internal sealed class Interactive {
    object Retry : Interactive()
    object Confirm : Interactive()
    data class Suggestions(val options: List<String>) : Interactive()

    companion object {
        fun parse(json: JSONObject?): Interactive? = when (json?.optString("type")) {
            "retry" -> Retry
            "confirm" -> Confirm
            "suggestions" -> {
                val options = json.optJSONArray("options") ?: JSONArray()
                Suggestions((0 until options.length()).map { options.getString(it) })
            }
            else -> null
        }
    }
}

/** One message as the chat shows it: from REST, a socket event, or an optimistic send. */
internal data class ChatMessage(
    val id: Int,
    val role: Role,
    val content: String,
    val createdAt: String,
    val deliveredAt: String? = null,
    val seenAt: String? = null,
    val senderName: String? = null,
    val senderAvatarUrl: String? = null,
    val shouldHandoff: Boolean = false,
    val feedback: String? = null,
    val sources: List<Source> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val interactive: Interactive? = null,
    val localImage: ByteArray? = null,
) {
    val isPending get() = id < 0

    companion object {
        fun fromRest(json: JSONObject) = parse(json, Role.fromRest(json.getString("role"))).copy(feedback = json.str("customer_feedback"))

        fun fromEvent(json: JSONObject): ChatMessage {
            val role = Role.fromSenderType(json.getString("sender_type"))
            val message = parse(json, role)
            return if (role == Role.AGENT) message else message.copy(senderName = null)
        }

        private fun parse(json: JSONObject, role: Role) = ChatMessage(
            id = json.getInt("id"),
            role = role,
            content = json.str("content") ?: "",
            createdAt = json.optString("created_at"),
            deliveredAt = json.str("delivered_at"),
            seenAt = json.str("seen_at"),
            senderName = json.str("sender_name"),
            senderAvatarUrl = json.str("sender_avatar_url"),
            shouldHandoff = json.optBoolean("should_handoff", false),
            sources = json.optJSONArray("ai_sources").objects().map { Source(it.optString("title"), it.str("url")) },
            attachments = json.optJSONArray("attachments").objects().map { Attachment(it.str("name"), it.getString("url"), it.str("type")) },
            interactive = Interactive.parse(json.optJSONObject("interactive")),
        )
    }
}

internal fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }
