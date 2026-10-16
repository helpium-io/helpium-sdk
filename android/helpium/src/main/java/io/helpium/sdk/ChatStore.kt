package io.helpium.sdk

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.Executors

/**
 * The chat's state and every rule of spec/API.md "Behavior the SDKs
 * replicate". Lives on the main thread; network calls run on a worker.
 */
internal class ChatStore(context: Context, val api: Api) {
    enum class Screen { LOADING, HOME, THREAD, UNAVAILABLE, FAILED }

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val storage = Storage(appContext)
    private val listeners = mutableSetOf<() -> Unit>()
    var onUnreadCountChange: ((Int) -> Unit)? = null

    var screen = Screen.LOADING; private set
    var settings: WidgetSettings? = null; private set
    var messages: List<ChatMessage> = emptyList(); private set
    var status: String? = null; private set
    var agent: Agent? = null; private set
    var handoffRequested = false; private set
    var closedReason: String? = null; private set
    var showRating = false; private set
    var isBotTyping = false; private set
    var botTypingStatus: String? = null; private set
    var isAgentTyping = false; private set
    var customerIdentified = false; private set
    var unreadCount = 0; private set
    var notice: String? = null

    /** Set by the chat screen: while visible, team messages count as seen. */
    var isVisible = false
        set(value) { field = value; if (value) markAllSeen() }

    var token: String? = null; private set
    var identity = Identity(); private set
    var explicitLocale: String? = null
    private var conversationId: Int? = null
    private var socket: PusherSocket? = null
    private var pushToken: String? = null
    private var nextTempId = -1
    private val pendingDelivered = mutableSetOf<Int>()
    private val pendingSeen = mutableSetOf<Int>()
    private val flush = Runnable { flushStatuses() }
    private val stopAgentTyping = Runnable { isAgentTyping = false; changed() }
    private var lastTypingSent = 0L
    private var stopTyping: Runnable? = null
    private var started = false

    val hasConversation get() = token != null && conversationId != null
    val isClosed get() = status == "closed"

    val needsPrechat: Boolean
        get() {
            val s = settings ?: return false
            return s.requireCustomerDetails && identity.email == null && identity.jwtClaim("email") == null && !customerIdentified && !hasConversation
        }

    private val aiWillRespond: Boolean
        get() {
            val s = settings ?: return false
            return agent == null && !handoffRequested && s.enableBot && s.aiAvailable && s.aiReplyMode != "draft"
        }

    val showHandoffButton: Boolean
        get() {
            val s = settings ?: return false
            if (s.humanHandoffMode == "disabled" || handoffRequested || isClosed) return false
            return messages.lastOrNull { it.role == Role.BOT }?.shouldHandoff == true
        }

    fun addListener(listener: () -> Unit) { listeners += listener }
    fun removeListener(listener: () -> Unit) { listeners -= listener }
    private fun changed() { listeners.toList().forEach { it() } }

    private fun <T> background(work: () -> T, done: (Result<T>) -> Unit = {}) {
        worker.execute {
            val result = runCatching(work)
            main.post { done(result) }
        }
    }

    // region Lifecycle

    fun start() {
        if (started) return
        started = true
        screen = Screen.LOADING
        changed()

        val locale = Strings.locale(appContext)
        background({
            val settings = api.settings(locale)
            val identified = identifyIfVerified()
            val token = identified?.widgetToken ?: storage.conversationToken(api.widgetKey, identity.storageKey)
            val conversation = (identified ?: token?.let { api.conversation(it) })
            Triple(settings, token, conversation)
        }) { result ->
            result.onFailure { error ->
                started = false
                screen = if ((error as? HelpiumException)?.status == 402) Screen.UNAVAILABLE else Screen.FAILED
                changed()
            }
            result.onSuccess { (settings, token, conversation) ->
                this.settings = settings
                connectSocket(settings)
                if (token != null) resume(token, conversation)
                screen = if (hasConversation || showRating) Screen.THREAD else Screen.HOME
                changed()
            }
        }
    }

    fun reload() {
        started = false
        start()
    }

    /** Runs on the worker: a verified identity picks up the user's open conversation. */
    private fun identifyIfVerified(): Conversation? {
        val jwt = identity.userJwt ?: return null
        val email = identity.email ?: identity.jwtClaim("email") ?: return null
        val body = JSONObject().put("email", email).put("user_jwt", jwt)
        identity.name?.let { body.put("name", it) }
        val result = runCatching { api.identify(body) }.getOrNull() ?: return null
        if (!result.optBoolean("verified")) return null
        main.post { customerIdentified = true }
        return result.optJSONObject("active_conversation")?.let(Conversation::parse)
    }

    private fun resume(token: String, conversation: Conversation?) {
        if (conversation == null) {
            forgetConversation()
            return
        }
        val waitingForRating = conversation.status == "closed" && !conversation.rated && settings?.conversationRatingsEnabled == true
        if (conversation.status == "closed" && !waitingForRating) {
            forgetConversation()
            return
        }
        adopt(conversation, token)
        showRating = waitingForRating
    }

    private fun adopt(conversation: Conversation, token: String) {
        this.token = token
        storage.setConversationToken(token, api.widgetKey, identity.storageKey)

        if (conversationId != conversation.id) {
            conversationId?.let(::unsubscribe)
            conversationId = conversation.id
            subscribe(conversation.id)
        }

        status = conversation.status
        agent = conversation.assignedAgent
        handoffRequested = conversation.status == "pending"
        closedReason = conversation.closedReason
        customerIdentified = customerIdentified || conversation.customerIdentified
        conversation.messages?.let { list ->
            messages = list
            list.filter { it.role.isFromTeam && it.deliveredAt == null }.forEach { pendingDelivered += it.id }
            scheduleFlush()
            if (isVisible) markAllSeen()
        }
        refreshUnread()
        registerDevice()
        changed()
    }

    private fun forgetConversation() {
        storage.setConversationToken(null, api.widgetKey, identity.storageKey)
        forgetConversationLocally()
        handoffRequested = false
        closedReason = null
        isBotTyping = false
        isAgentTyping = false
        changed()
    }

    /** Drops the conversation from memory but keeps its token for the same user. */
    private fun forgetConversationLocally() {
        conversationId?.let(::unsubscribe)
        token = null
        conversationId = null
        messages = emptyList()
        status = null
        agent = null
        showRating = false
        refreshUnread()
    }

    // endregion

    // region Identity

    fun setIdentity(identity: Identity) {
        if (identity == this.identity) return
        val userChanged = identity.storageKey != this.identity.storageKey
        this.identity = identity
        if (userChanged) {
            forgetConversationLocally()
            if (started) reload()
        }
    }

    fun logout() {
        pushToken?.let { push -> background({ api.unregisterDevice(push) }) }
        forgetConversationLocally()
        identity = Identity()
        customerIdentified = false
        screen = if (settings == null) Screen.LOADING else Screen.HOME
        changed()
    }

    fun submitPrechat(firstName: String, lastName: String, email: String, marketingOptIn: Boolean) {
        identity = identity.copy(
            firstName = firstName,
            lastName = lastName,
            email = email,
            name = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" "),
            marketingOptIn = marketingOptIn,
        )
        customerIdentified = true
    }

    // endregion

    // region Navigation

    fun openThread() {
        screen = Screen.THREAD
        markAllSeen()
        changed()
    }

    fun goHome() {
        if (isClosed && !showRating) forgetConversation()
        screen = Screen.HOME
        changed()
    }

    fun startNewConversation() {
        forgetConversation()
        screen = Screen.THREAD
        changed()
    }

    // endregion

    // region Sending

    fun send(text: String, image: ByteArray? = null, imageType: String = "image/jpeg", instantAnswerId: Int? = null) {
        val content = text.trim()
        if ((content.isEmpty() && image == null) || isClosed || settings == null) return

        val tempId = nextTempId--
        messages = messages + ChatMessage(id = tempId, role = Role.CUSTOMER, content = content, createdAt = now(), localImage = image)
        if (aiWillRespond) {
            isBotTyping = true
            botTypingStatus = null
        }
        screen = Screen.THREAD
        changed()

        val existingToken = token
        val payload = JSONObject()
        if (content.isNotEmpty()) payload.put(if (existingToken == null) "message" else "content", content)
        if (image != null) {
            val ext = imageType.substringAfter('/')
            payload.put("image", JSONObject()
                .put("data", Base64.encodeToString(image, Base64.NO_WRAP))
                .put("name", "photo.$ext")
                .put("type", imageType))
        }
        instantAnswerId?.let { payload.put("instant_answer_id", it) }
        explicitLocale?.let { payload.put("language", it) }

        if (existingToken != null) {
            background({ api.sendMessage(existingToken, payload) }) { result ->
                result.onSuccess { replaceOptimistic(tempId, it.firstOrNull()) }
                result.onFailure { sendFailed(tempId, it) }
            }
        } else {
            val newToken = UUID.randomUUID().toString()
            payload.put("widget_token", newToken)
            customerPayload()?.let { payload.put("customer", it) }
            background({ api.createConversation(payload) }) { result ->
                result.onSuccess { conversation ->
                    adopt(conversation.copy(messages = null), newToken)
                    replaceOptimistic(tempId, conversation.messages?.firstOrNull())
                }
                result.onFailure { sendFailed(tempId, it) }
            }
        }
    }

    private fun sendFailed(tempId: Int, error: Throwable) {
        messages = messages.filterNot { it.id == tempId }
        isBotTyping = false
        val api = error as? HelpiumException
        if (api?.code == "conversation_closed") status = "closed"
        notice = if (api?.status == 429) api.message else Strings.get(appContext, R.string.helpium_send_failed)
        changed()
    }

    private fun customerPayload(): JSONObject? {
        val customer = JSONObject()
        identity.userJwt?.let { customer.put("userJwt", it) }
        identity.email?.let { customer.put("email", it) }
        identity.name?.takeIf { it.isNotBlank() }?.let { customer.put("name", it) }
        identity.firstName?.takeIf { it.isNotBlank() }?.let { customer.put("first_name", it) }
        identity.lastName?.takeIf { it.isNotBlank() }?.let { customer.put("last_name", it) }
        identity.marketingOptIn?.let { customer.put("marketing_opt_in", it) }
        return if (customer.length() == 0) null else customer
    }

    private fun replaceOptimistic(tempId: Int, message: ChatMessage?) {
        val index = messages.indexOfFirst { it.id == tempId }
        if (index < 0 || message == null) return
        messages = if (messages.any { it.id == message.id }) {
            messages.filterNot { it.id == tempId }
        } else {
            messages.toMutableList().also { it[index] = message.copy(localImage = messages[index].localImage) }
        }
        changed()
    }

    fun sendInstantAnswer(answer: WidgetSettings.InstantAnswer) {
        send(answer.label, instantAnswerId = if (answer.isShortcut) null else answer.id)
    }

    fun requestHuman() {
        val token = token ?: return
        handoffRequested = true
        changed()
        background({ api.requestHuman(token) }) { result -> result.onSuccess { status = it.status; changed() } }
    }

    fun close() {
        val token = token ?: return
        background({ api.close(token) }) { result ->
            result.onSuccess {
                status = it.status
                closedReason = "customer"
                isBotTyping = false
                if (settings?.conversationRatingsEnabled == true) {
                    showRating = true
                } else {
                    storage.setConversationToken(null, api.widgetKey, identity.storageKey)
                }
                changed()
            }
            result.onFailure {
                notice = Strings.get(appContext, R.string.helpium_send_failed)
                changed()
            }
        }
    }

    fun rate(rating: Int, comment: String?) {
        val token = token ?: return
        background({ api.rate(token, rating, comment) }) {
            forgetConversation()
            screen = Screen.HOME
            changed()
        }
    }

    fun skipRating() {
        forgetConversation()
        screen = Screen.HOME
        changed()
    }

    fun feedback(messageId: Int, up: Boolean) {
        val token = token ?: return
        val value = if (up) "up" else "down"
        update(messageId) { it.copy(feedback = value) }
        background({ api.feedback(token, messageId, value) })
    }

    fun retry(messageId: Int) {
        val token = token ?: return
        update(messageId) { it.copy(interactive = null) }
        isBotTyping = true
        changed()
        background({ api.retry(token, messageId) })
    }

    /** Tells the agent the customer is typing: at most every 2 s, and stopped after 3 s idle. */
    fun customerTyping() {
        val id = conversationId ?: return
        val channel = "private-conversation.$id.typing"
        val name = identity.name ?: identity.email
        fun whisper(typing: Boolean) = socket?.trigger(channel, "client-typing", JSONObject()
            .put("sender_type", "customer").put("sender_name", name ?: JSONObject.NULL).put("is_typing", typing))

        if (System.currentTimeMillis() - lastTypingSent > 2000) {
            lastTypingSent = System.currentTimeMillis()
            whisper(true)
        }
        stopTyping?.let(main::removeCallbacks)
        stopTyping = Runnable { lastTypingSent = 0; whisper(false) }.also { main.postDelayed(it, 3000) }
    }

    // endregion

    // region Push

    fun setPushToken(token: String) {
        if (token == pushToken) return
        pushToken = token
        registerDevice()
    }

    private fun registerDevice() {
        val push = pushToken ?: return
        val token = token ?: return
        val body = JSONObject()
            .put("widget_token", token)
            .put("push_token", push)
            .put("platform", "android")
            .put("app_id", appContext.packageName)
            .put("sdk_version", BuildConfig.SDK_VERSION)
        background({ api.registerDevice(body) })
    }

    /** Opens the conversation a notification is about. */
    fun open(conversationToken: String) {
        if (conversationToken == token) {
            reconcile(conversationToken)
            screen = Screen.THREAD
            changed()
            return
        }
        background({ api.conversation(conversationToken) }) { result ->
            resume(conversationToken, result.getOrNull())
            if (hasConversation) screen = Screen.THREAD
            changed()
        }
    }

    // endregion

    // region Delivered / seen

    private fun markAllSeen() {
        if (!isVisible || screen != Screen.THREAD) return
        val now = now()
        var touched = false
        messages = messages.map {
            if (it.role.isFromTeam && it.seenAt == null && !it.isPending) {
                pendingSeen += it.id
                touched = true
                it.copy(seenAt = now)
            } else it
        }
        if (touched) {
            refreshUnread()
            scheduleFlush()
        }
    }

    private fun scheduleFlush() {
        main.removeCallbacks(flush)
        main.postDelayed(flush, 400)
    }

    private fun flushStatuses() {
        val token = token ?: return
        val delivered = (pendingDelivered - pendingSeen).toList()
        val seen = pendingSeen.toList()
        pendingDelivered.clear()
        pendingSeen.clear()
        background({
            delivered.chunked(100).forEach { api.updateStatus(token, "delivered", it) }
            seen.chunked(100).forEach { api.updateStatus(token, "seen", it) }
        })
    }

    private fun refreshUnread() {
        val count = messages.count { it.role.isFromTeam && it.seenAt == null && !it.isPending }
        if (count == unreadCount) return
        unreadCount = count
        onUnreadCountChange?.invoke(count)
    }

    // endregion

    // region Realtime

    private fun connectSocket(settings: WidgetSettings) {
        if (socket != null) return
        val ws = settings.websocket
        val base = java.net.URI(api.baseUrl)
        val secure = if (ws.useApiHost) base.scheme == "https" else ws.scheme == "https"
        val host = if (ws.useApiHost) base.host else ws.host
        val port = if (ws.useApiHost) (if (base.port > 0) base.port else if (secure) 443 else 80) else ws.port

        socket = PusherSocket(host, port, secure, ws.appKey, api.http) { socketId, channel -> api.broadcastingAuth(socketId, channel) }.also {
            it.onEvent = { _, event, data -> handle(event, data) }
            it.onConnected = { token?.let(::reconcile) }
            it.connect()
        }
    }

    private fun subscribe(id: Int) {
        socket?.subscribe("conversation.$id")
        socket?.subscribe("private-conversation.$id.typing")
    }

    private fun unsubscribe(id: Int) {
        socket?.unsubscribe("conversation.$id")
        socket?.unsubscribe("private-conversation.$id.typing")
    }

    /** Catches up on what arrived while the socket was away. */
    private fun reconcile(token: String) {
        background({ api.messages(token) }) { result ->
            result.getOrNull()?.forEach(::merge)
            if (messages.lastOrNull()?.role?.isFromTeam == true) isBotTyping = false
            changed()
        }
    }

    private fun merge(message: ChatMessage) {
        val existing = messages.indexOfFirst { it.id == message.id }
        if (existing >= 0) {
            val localSeen = messages[existing].seenAt
            messages = messages.toMutableList().also { it[existing] = if (message.seenAt == null) message.copy(seenAt = localSeen) else message }
            return
        }
        if (message.role == Role.CUSTOMER) {
            val pending = messages.indexOfFirst { it.isPending && it.role == Role.CUSTOMER && it.content == message.content }
            if (pending >= 0) {
                messages = messages.toMutableList().also { it[pending] = message.copy(localImage = messages[pending].localImage) }
                return
            }
        }
        messages = messages + message
        if (message.role.isFromTeam) {
            pendingDelivered += message.id
            scheduleFlush()
            if (isVisible) markAllSeen() else refreshUnread()
        }
    }

    private fun update(id: Int, change: (ChatMessage) -> ChatMessage) {
        messages = messages.map { if (it.id == id) change(it) else it }
        changed()
    }

    fun handle(event: String, data: String) {
        val json = runCatching { JSONObject(data) }.getOrNull() ?: return
        when (event) {
            "App\\Events\\NewMessage" -> {
                if (json.optBoolean("is_internal", false)) return
                if (json.optBoolean("content_truncated", false)) {
                    token?.let(::reconcile)
                    return
                }
                val message = ChatMessage.fromEvent(json)
                if (message.role.isFromTeam) {
                    isBotTyping = false
                    botTypingStatus = null
                    isAgentTyping = false
                }
                merge(message)
            }
            "App\\Events\\ConversationUpdated" -> {
                Agent.parse(json.optJSONObject("assigned_user"))?.let { agent = it }
                json.str("status")?.let { newStatus ->
                    status = newStatus
                    if (newStatus == "pending") handoffRequested = true
                    if (newStatus == "closed") {
                        isBotTyping = false
                        if (closedReason == null) closedReason = if (json.str("reason") == "inactivity") "inactivity" else "agent"
                        if (settings?.conversationRatingsEnabled == true) showRating = true
                    }
                }
            }
            "App\\Events\\MessageStatusUpdated" -> {
                val id = json.optInt("id")
                messages = messages.map {
                    if (it.id == id) it.copy(deliveredAt = json.str("delivered_at") ?: it.deliveredAt, seenAt = json.str("seen_at") ?: it.seenAt) else it
                }
                refreshUnread()
            }
            "App\\Events\\BotTyping" -> {
                if (agent != null) return
                isBotTyping = json.optBoolean("is_typing")
                botTypingStatus = json.str("status")
            }
            "App\\Events\\MessageInteractiveUpdated" -> {
                val id = json.optInt("id")
                val interactive = Interactive.parse(json.optJSONObject("interactive"))
                messages = messages.map { if (it.id == id) it.copy(interactive = interactive) else it }
            }
            "client-typing" -> {
                if (json.optString("sender_type") != "agent" || isBotTyping) return
                isAgentTyping = json.optBoolean("is_typing")
                main.removeCallbacks(stopAgentTyping)
                if (isAgentTyping) main.postDelayed(stopAgentTyping, 6000)
            }
            else -> return
        }
        changed()
    }

    // endregion

    companion object {
        fun now(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())

        @Suppress("unused")
        val deviceName: String get() = "${Build.MANUFACTURER} ${Build.MODEL}"
    }
}
