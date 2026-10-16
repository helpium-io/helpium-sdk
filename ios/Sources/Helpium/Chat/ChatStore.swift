import Foundation

/// The chat's state and every rule of spec/API.md "Behavior the SDKs
/// replicate". The SwiftUI views only render it and call its actions.
@MainActor
final class ChatStore: ObservableObject {
    enum Screen: Equatable { case loading, home, thread, unavailable, failed }

    struct Identity: Equatable {
        var userJwt: String?
        var email: String?
        var name: String?
        var firstName: String?
        var lastName: String?
        var marketingOptIn: Bool?

        /// Scopes the stored conversation to the signed-in user.
        var storageKey: String? { jwtClaim("user_id") ?? jwtClaim("email") ?? email }

        func jwtClaim(_ name: String) -> String? {
            guard let userJwt else { return nil }
            let parts = userJwt.split(separator: ".")
            guard parts.count == 3 else { return nil }
            var payload = String(parts[1]).replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
            while payload.count % 4 != 0 { payload += "=" }
            guard let data = Data(base64Encoded: payload),
                  let claims = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let value = claims[name] else { return nil }
            return "\(value)"
        }
    }

    @Published private(set) var screen: Screen = .loading
    @Published private(set) var settings: WidgetSettings?
    @Published private(set) var messages: [ChatMessage] = []
    @Published private(set) var status: String?
    @Published private(set) var agent: Agent?
    @Published private(set) var handoffRequested = false
    @Published private(set) var closedReason: String?
    @Published private(set) var showRating = false
    @Published private(set) var isBotTyping = false
    @Published private(set) var botTypingStatus: String?
    @Published private(set) var isAgentTyping = false
    @Published private(set) var customerIdentified = false
    @Published var notice: String?
    @Published private(set) var unreadCount = 0
    @Published var appearance = HelpiumAppearance()

    var onUnreadCountChange: ((Int) -> Void)?

    /// Set by the chat view: while visible, team messages count as seen.
    var isVisible = false {
        didSet { if isVisible { markAllSeen() } }
    }

    let api: APIClient
    private let storage = Storage()
    private var socket: PusherSocket?
    private var conversationId: Int?
    private(set) var token: String?
    private(set) var identity = Identity()
    private(set) var explicitLocale: String?
    private var pushToken: String?
    private var nextTempId = -1
    private var pendingDelivered = Set<Int>()
    private var pendingSeen = Set<Int>()
    private var flushTask: Task<Void, Never>?
    private var agentTypingTask: Task<Void, Never>?
    private var lastTypingSent = Date.distantPast
    private var stopTypingTask: Task<Void, Never>?
    private var started = false

    init(api: APIClient) {
        self.api = api
    }

    var hasConversation: Bool { token != nil && conversationId != nil }
    var isClosed: Bool { status == "closed" }

    var needsPrechat: Bool {
        guard let settings, settings.requireCustomerDetails else { return false }
        return identity.email == nil && identity.jwtClaim("email") == nil && !customerIdentified && !hasConversation
    }

    /// The bot answers on its own: what makes the typing dots appear right
    /// after a send.
    private var aiWillRespond: Bool {
        guard let settings else { return false }
        return agent == nil && !handoffRequested && settings.enableBot && settings.aiAvailable && settings.aiReplyMode != "draft"
    }

    var showHandoffButton: Bool {
        guard let settings, settings.humanHandoffMode != "disabled", !handoffRequested, !isClosed else { return false }
        guard let last = messages.last(where: { $0.role == .bot }) else { return false }
        return last.shouldHandoff
    }

    // MARK: - Lifecycle

    func start() async {
        guard !started else { return }
        started = true
        await load()
    }

    private func load() async {
        screen = .loading
        do {
            let settings = try await api.settings(locale: L.locale)
            self.settings = settings
            connectSocket(settings)
        } catch let error as HelpiumError where error.status == 402 {
            screen = .unavailable
            return
        } catch {
            screen = .failed
            started = false
            return
        }

        await identifyIfVerified()

        if token == nil {
            token = storage.conversationToken(widgetKey: api.widgetKey, userKey: identity.storageKey)
        }

        if let token {
            await resume(token: token)
        }

        screen = hasConversation || showRating ? .thread : .home
    }

    func reload() async {
        started = false
        await start()
    }

    private func resume(token: String) async {
        guard let conversation = try? await api.conversation(token: token) else {
            forgetConversation()
            return
        }

        let waitingForRating = conversation.status == "closed" && !conversation.rated && (settings?.conversationRatingsEnabled ?? false)

        if conversation.status == "closed" && !waitingForRating {
            forgetConversation()
            return
        }

        adopt(conversation, token: token)
        showRating = waitingForRating
    }

    private func adopt(_ conversation: ConversationDTO, token: String) {
        self.token = token
        storage.setConversationToken(token, widgetKey: api.widgetKey, userKey: identity.storageKey)

        if conversationId != conversation.id {
            if let old = conversationId { unsubscribe(old) }
            conversationId = conversation.id
            subscribe(conversation.id)
        }

        status = conversation.status
        agent = conversation.assignedAgent
        handoffRequested = conversation.status == "pending"
        closedReason = conversation.closedReason
        customerIdentified = conversation.customerIdentified
        if let list = conversation.messages {
            messages = list.map(\.message)
            messages.filter(\.isFromTeam).filter { $0.deliveredAt == nil }.forEach { pendingDelivered.insert($0.id) }
            scheduleFlush()
            if isVisible { markAllSeen() }
        }
        refreshUnread()
        registerDevice()
    }

    private func forgetConversation() {
        if let id = conversationId { unsubscribe(id) }
        storage.setConversationToken(nil, widgetKey: api.widgetKey, userKey: identity.storageKey)
        token = nil
        conversationId = nil
        messages = []
        status = nil
        agent = nil
        handoffRequested = false
        closedReason = nil
        showRating = false
        isBotTyping = false
        isAgentTyping = false
        refreshUnread()
    }

    // MARK: - Identity

    func setIdentity(_ identity: Identity) {
        guard identity != self.identity else { return }
        let userChanged = identity.storageKey != self.identity.storageKey
        self.identity = identity
        if userChanged {
            forgetConversationLocally()
            if started { Task { await self.reload() } }
        }
    }

    func setLocale(_ locale: String?) {
        explicitLocale = locale
    }

    func logout() {
        if let pushToken { Task { try? await api.unregisterDevice(pushToken: pushToken) } }
        forgetConversationLocally()
        identity = Identity()
        customerIdentified = false
        screen = settings == nil ? .loading : .home
    }

    /// Drops the conversation from memory without forgetting its token, so the
    /// same user gets it back after signing in again.
    private func forgetConversationLocally() {
        if let id = conversationId { unsubscribe(id) }
        token = nil
        conversationId = nil
        messages = []
        status = nil
        agent = nil
        showRating = false
        refreshUnread()
    }

    private func identifyIfVerified() async {
        guard let jwt = identity.userJwt,
              let email = identity.email ?? identity.jwtClaim("email") else { return }

        var body: [String: Any] = ["email": email, "user_jwt": jwt]
        if let name = identity.name { body["name"] = name }

        guard let result = try? await api.identify(body), result.verified else { return }
        customerIdentified = true

        if let active = result.activeConversation, let activeToken = active.widgetToken {
            adopt(active, token: activeToken)
        }
    }

    /// The pre-chat form: name and email before the first message.
    func submitPrechat(firstName: String, lastName: String, email: String, marketingOptIn: Bool) {
        identity.firstName = firstName
        identity.lastName = lastName
        identity.email = email
        identity.name = [firstName, lastName].filter { !$0.isEmpty }.joined(separator: " ")
        identity.marketingOptIn = marketingOptIn
        customerIdentified = true
    }

    // MARK: - Navigation

    func openThread() {
        screen = .thread
        markAllSeen()
    }

    func goHome() {
        if isClosed && !showRating { forgetConversation() }
        screen = .home
    }

    func startNewConversation() {
        forgetConversation()
        screen = .thread
    }

    // MARK: - Sending

    func send(text: String, image: (data: Data, mimeType: String)? = nil, instantAnswerId: Int? = nil) {
        let content = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard (!content.isEmpty || image != nil), !isClosed, settings != nil else { return }

        let tempId = nextTempId
        nextTempId -= 1
        messages.append(ChatMessage(
            id: tempId, role: .customer, content: content, createdAt: ISO8601DateFormatter().string(from: Date()),
            deliveredAt: nil, seenAt: nil, senderName: nil, senderAvatarUrl: nil, shouldHandoff: false,
            feedback: nil, sources: [], attachments: [], interactive: nil, localImage: image?.data
        ))
        if aiWillRespond { isBotTyping = true; botTypingStatus = nil }
        screen = .thread

        var payload: [String: Any] = [:]
        if !content.isEmpty { payload[token == nil ? "message" : "content"] = content }
        if let image {
            let ext = image.mimeType.split(separator: "/").last.map(String.init) ?? "jpg"
            payload["image"] = ["data": image.data.base64EncodedString(), "name": "photo.\(ext)", "type": image.mimeType]
        }
        if let instantAnswerId { payload["instant_answer_id"] = instantAnswerId }
        if let explicitLocale { payload["language"] = explicitLocale }

        Task {
            do {
                if let token {
                    let sent = try await api.sendMessage(token: token, payload)
                    replaceOptimistic(tempId, with: sent.first?.message)
                } else {
                    let newToken = UUID().uuidString.lowercased()
                    payload["widget_token"] = newToken
                    if let customer = customerPayload() { payload["customer"] = customer }
                    let conversation = try await api.createConversation(payload)
                    let confirmed = conversation.messages?.first?.message
                    adopt(ConversationDTO(id: conversation.id, status: conversation.status, assignedAgent: conversation.assignedAgent, closedReason: conversation.closedReason, rated: conversation.rated, customerIdentified: conversation.customerIdentified, messages: nil, widgetToken: nil), token: newToken)
                    replaceOptimistic(tempId, with: confirmed)
                }
            } catch let error as HelpiumError {
                messages.removeAll { $0.id == tempId }
                isBotTyping = false
                if error.code == "conversation_closed" { status = "closed" }
                notice = error.status == 429 ? error.message : L.t("send_failed")
            } catch {
                messages.removeAll { $0.id == tempId }
                isBotTyping = false
                notice = L.t("send_failed")
            }
        }
    }

    private func customerPayload() -> [String: Any]? {
        var customer: [String: Any] = [:]
        if let jwt = identity.userJwt { customer["userJwt"] = jwt }
        if let email = identity.email { customer["email"] = email }
        if let name = identity.name, !name.isEmpty { customer["name"] = name }
        if let first = identity.firstName, !first.isEmpty { customer["first_name"] = first }
        if let last = identity.lastName, !last.isEmpty { customer["last_name"] = last }
        if let optIn = identity.marketingOptIn { customer["marketing_opt_in"] = optIn }
        return customer.isEmpty ? nil : customer
    }

    private func replaceOptimistic(_ tempId: Int, with message: ChatMessage?) {
        guard let index = messages.firstIndex(where: { $0.id == tempId }) else { return }
        guard var message else { return }
        if messages.contains(where: { $0.id == message.id }) {
            messages.remove(at: index)
            return
        }
        message.localImage = messages[index].localImage
        messages[index] = message
    }

    func sendInstantAnswer(_ answer: WidgetSettings.InstantAnswer) {
        send(text: answer.label, instantAnswerId: answer.isShortcut ? nil : answer.id)
    }

    func requestHuman() {
        guard let token else { return }
        handoffRequested = true
        Task {
            if let conversation = try? await api.requestHuman(token: token) {
                status = conversation.status
            }
        }
    }

    func close() {
        guard let token else { return }
        Task {
            do {
                let conversation = try await api.close(token: token)
                status = conversation.status
                closedReason = "customer"
                isBotTyping = false
                if settings?.conversationRatingsEnabled == true {
                    showRating = true
                } else {
                    storage.setConversationToken(nil, widgetKey: api.widgetKey, userKey: identity.storageKey)
                }
            } catch {
                notice = L.t("send_failed")
            }
        }
    }

    func rate(_ rating: Int, comment: String?) {
        guard let token else { return }
        Task {
            try? await api.rate(token: token, rating: rating, comment: comment)
            forgetConversation()
            screen = .home
        }
    }

    func skipRating() {
        forgetConversation()
        screen = .home
    }

    func feedback(messageId: Int, up: Bool) {
        guard let token, let index = messages.firstIndex(where: { $0.id == messageId }) else { return }
        let value = up ? "up" : "down"
        messages[index].feedback = value
        Task { try? await api.feedback(token: token, messageId: messageId, value: value) }
    }

    func retry(messageId: Int) {
        guard let token, let index = messages.firstIndex(where: { $0.id == messageId }) else { return }
        messages[index].interactive = nil
        isBotTyping = true
        Task { try? await api.retry(token: token, messageId: messageId) }
    }

    /// The customer is typing: tell the agent, at most every 2 s, and that
    /// they stopped after 3 s without a keystroke.
    func customerTyping() {
        guard let conversationId else { return }
        let channel = "private-conversation.\(conversationId).typing"
        let name = identity.name ?? identity.email
        if Date().timeIntervalSince(lastTypingSent) > 2 {
            lastTypingSent = Date()
            socket?.trigger(channel: channel, event: "client-typing", data: ["sender_type": "customer", "sender_name": name as Any, "is_typing": true])
        }
        stopTypingTask?.cancel()
        stopTypingTask = Task {
            try? await Task.sleep(nanoseconds: 3_000_000_000)
            guard !Task.isCancelled else { return }
            self.lastTypingSent = .distantPast
            self.socket?.trigger(channel: channel, event: "client-typing", data: ["sender_type": "customer", "sender_name": name as Any, "is_typing": false])
        }
    }

    // MARK: - Push

    func setPushToken(_ token: String) {
        guard token != pushToken else { return }
        pushToken = token
        registerDevice()
    }

    private func registerDevice() {
        guard let pushToken, let token else { return }
        Task {
            try? await api.registerDevice([
                "widget_token": token,
                "push_token": pushToken,
                "platform": "ios",
                "app_id": Bundle.main.bundleIdentifier as Any,
                "sdk_version": HelpiumInfo.version,
            ])
        }
    }

    /// Opens the conversation a notification is about.
    func open(conversationToken: String) async {
        if conversationToken != token {
            await resume(token: conversationToken)
        } else if let token {
            await reconcile(token: token)
        }
        if hasConversation { screen = .thread }
    }

    // MARK: - Delivered / seen

    private func markAllSeen() {
        guard isVisible, screen == .thread else { return }
        let now = ISO8601DateFormatter().string(from: Date())
        for index in messages.indices where messages[index].isFromTeam && messages[index].seenAt == nil && !messages[index].isPending {
            messages[index].seenAt = now
            pendingSeen.insert(messages[index].id)
        }
        refreshUnread()
        scheduleFlush()
    }

    private func scheduleFlush() {
        flushTask?.cancel()
        flushTask = Task {
            try? await Task.sleep(nanoseconds: 400_000_000)
            guard !Task.isCancelled, let token = self.token else { return }
            let delivered = Array(self.pendingDelivered.subtracting(self.pendingSeen))
            let seen = Array(self.pendingSeen)
            self.pendingDelivered.removeAll()
            self.pendingSeen.removeAll()
            for (status, ids) in [("delivered", delivered), ("seen", seen)] where !ids.isEmpty {
                for chunk in stride(from: 0, to: ids.count, by: 100).map({ Array(ids[$0..<min($0 + 100, ids.count)]) }) {
                    try? await self.api.updateStatus(token: token, status: status, ids: chunk)
                }
            }
        }
    }

    private func refreshUnread() {
        let count = messages.filter { $0.isFromTeam && $0.seenAt == nil && !$0.isPending }.count
        guard count != unreadCount else { return }
        unreadCount = count
        onUnreadCountChange?(count)
    }

    // MARK: - Realtime

    private func connectSocket(_ settings: WidgetSettings) {
        guard socket == nil else { return }
        let ws = settings.websocket
        let config: PusherSocket.Config
        if ws.useApiHost == true {
            let secure = api.baseURL.scheme == "https"
            config = .init(key: ws.appKey, host: api.baseURL.host ?? ws.host, port: api.baseURL.port ?? (secure ? 443 : 80), useTLS: secure)
        } else {
            config = .init(key: ws.appKey, host: ws.host, port: ws.port, useTLS: ws.scheme == "https")
        }

        let api = self.api
        let socket = PusherSocket(config: config) { socketId, channel in
            try await api.broadcastingAuth(socketId: socketId, channel: channel)
        }
        socket.onEvent = { [weak self] channel, event, data in self?.handle(event: event, data: data) }
        socket.onConnected = { [weak self] in
            guard let self, let token = self.token else { return }
            Task { await self.reconcile(token: token) }
        }
        self.socket = socket
        socket.connect()
    }

    private func subscribe(_ id: Int) {
        socket?.subscribe("conversation.\(id)")
        socket?.subscribe("private-conversation.\(id).typing")
    }

    private func unsubscribe(_ id: Int) {
        socket?.unsubscribe("conversation.\(id)")
        socket?.unsubscribe("private-conversation.\(id).typing")
    }

    /// Catches up on what arrived while the socket was away.
    private func reconcile(token: String) async {
        guard let list = try? await api.messages(token: token) else { return }
        for message in list.map(\.message) { merge(message) }
        if messages.last?.isFromTeam == true { isBotTyping = false }
    }

    private func merge(_ message: ChatMessage) {
        if let index = messages.firstIndex(where: { $0.id == message.id }) {
            let localSeen = messages[index].seenAt
            messages[index] = message
            if message.seenAt == nil { messages[index].seenAt = localSeen }
            return
        }
        if message.role == .customer,
           let index = messages.firstIndex(where: { $0.isPending && $0.role == .customer && $0.content == message.content }) {
            var confirmed = message
            confirmed.localImage = messages[index].localImage
            messages[index] = confirmed
            return
        }
        messages.append(message)
        if message.isFromTeam {
            pendingDelivered.insert(message.id)
            scheduleFlush()
            if isVisible { markAllSeen() } else { refreshUnread() }
        }
    }

    func handle(event: String, data: Data) {
        let decoder = APIClient.decoder
        switch event {
        case "App\\Events\\NewMessage":
            guard let payload = try? decoder.decode(NewMessageEvent.self, from: data), payload.isInternal != true else { return }
            if payload.contentTruncated == true, let token {
                Task { await self.reconcile(token: token) }
                return
            }
            let message = payload.message
            if message.isFromTeam {
                isBotTyping = false
                botTypingStatus = nil
                isAgentTyping = false
            }
            merge(message)
        case "App\\Events\\ConversationUpdated":
            guard let payload = try? decoder.decode(ConversationUpdatedEvent.self, from: data) else { return }
            if let assigned = payload.assignedUser { agent = assigned }
            if let newStatus = payload.status {
                status = newStatus
                if newStatus == "pending" { handoffRequested = true }
                if newStatus == "closed" {
                    isBotTyping = false
                    if closedReason == nil { closedReason = payload.reason == "inactivity" ? "inactivity" : "agent" }
                    if settings?.conversationRatingsEnabled == true { showRating = true }
                }
            }
        case "App\\Events\\MessageStatusUpdated":
            guard let payload = try? decoder.decode(MessageStatusEvent.self, from: data),
                  let index = messages.firstIndex(where: { $0.id == payload.id }) else { return }
            messages[index].deliveredAt = payload.deliveredAt ?? messages[index].deliveredAt
            messages[index].seenAt = payload.seenAt ?? messages[index].seenAt
            refreshUnread()
        case "App\\Events\\BotTyping":
            guard let payload = try? decoder.decode(BotTypingEvent.self, from: data), agent == nil else { return }
            isBotTyping = payload.isTyping
            botTypingStatus = payload.status
        case "App\\Events\\MessageInteractiveUpdated":
            guard let payload = try? decoder.decode(InteractiveUpdatedEvent.self, from: data),
                  let index = messages.firstIndex(where: { $0.id == payload.id }) else { return }
            messages[index].interactive = Interactive(json: payload.interactive?.object)
        case "client-typing":
            guard let payload = try? decoder.decode(TypingWhisper.self, from: data), payload.senderType == "agent", !isBotTyping else { return }
            isAgentTyping = payload.isTyping
            agentTypingTask?.cancel()
            if payload.isTyping {
                agentTypingTask = Task {
                    try? await Task.sleep(nanoseconds: 6_000_000_000)
                    if !Task.isCancelled { self.isAgentTyping = false }
                }
            }
        default:
            break
        }
    }
}
