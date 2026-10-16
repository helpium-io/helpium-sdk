import Foundation

/// A minimal client for the Pusher protocol that Laravel Reverb speaks, over
/// `URLSessionWebSocketTask`, so the SDK ships without third-party code.
/// It reconnects with backoff and resubscribes every channel it was on.
@MainActor
final class PusherSocket: NSObject {
    struct Config: Equatable {
        let key: String
        let host: String
        let port: Int
        let useTLS: Bool
    }

    /// Signs a private channel subscription: returns the `auth` string.
    typealias Authorizer = (_ socketId: String, _ channel: String) async throws -> String

    var onEvent: ((_ channel: String, _ event: String, _ data: Data) -> Void)?
    var onConnected: (() -> Void)?

    private let config: Config
    private let authorizer: Authorizer
    private var task: URLSessionWebSocketTask?
    private var session: URLSession?
    private var socketId: String?
    private var channels: Set<String> = []
    private var attempts = 0
    private var stopped = true

    init(config: Config, authorizer: @escaping Authorizer) {
        self.config = config
        self.authorizer = authorizer
    }

    func connect() {
        stopped = false
        open()
    }

    func disconnect() {
        stopped = true
        task?.cancel(with: .normalClosure, reason: nil)
        task = nil
        socketId = nil
    }

    func subscribe(_ channel: String) {
        guard !channels.contains(channel) else { return }
        channels.insert(channel)
        if socketId != nil { sendSubscribe(channel) }
    }

    func unsubscribe(_ channel: String) {
        guard channels.remove(channel) != nil else { return }
        send(["event": "pusher:unsubscribe", "data": ["channel": channel]])
    }

    /// Client events (whispers) on a private channel, such as `client-typing`.
    func trigger(channel: String, event: String, data: [String: Any]) {
        guard socketId != nil, channels.contains(channel) else { return }
        send(["event": event, "channel": channel, "data": data])
    }

    // MARK: - Connection

    private func open() {
        let scheme = config.useTLS ? "wss" : "ws"
        let version = HelpiumInfo.version
        guard let url = URL(string: "\(scheme)://\(config.host):\(config.port)/app/\(config.key)?protocol=7&client=helpium-ios&version=\(version)&flash=false") else { return }

        let session = URLSession(configuration: .default)
        let task = session.webSocketTask(with: url)
        self.session = session
        self.task = task
        task.resume()
        receive(on: task)
    }

    private func receive(on task: URLSessionWebSocketTask) {
        task.receive { [weak self] result in
            Task { @MainActor in
                guard let self, self.task === task else { return }
                switch result {
                case .success(let message):
                    if case .string(let text) = message { self.handle(text) }
                    self.receive(on: task)
                case .failure:
                    self.scheduleReconnect()
                }
            }
        }
    }

    private func scheduleReconnect() {
        task = nil
        socketId = nil
        guard !stopped else { return }
        attempts += 1
        let delay = min(30.0, pow(2.0, Double(min(attempts, 5))))
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
            if !self.stopped && self.task == nil { self.open() }
        }
    }

    private func handle(_ text: String) {
        guard let raw = text.data(using: .utf8),
              let frame = try? JSONSerialization.jsonObject(with: raw) as? [String: Any],
              let event = frame["event"] as? String else { return }

        let dataString: String? = frame["data"] as? String
        let payload = dataString?.data(using: .utf8)
            ?? (frame["data"].flatMap { try? JSONSerialization.data(withJSONObject: $0) })
            ?? Data("{}".utf8)

        switch event {
        case "pusher:connection_established":
            let info = (try? JSONSerialization.jsonObject(with: payload)) as? [String: Any]
            socketId = info?["socket_id"] as? String
            attempts = 0
            channels.forEach(sendSubscribe)
            onConnected?()
        case "pusher:ping":
            send(["event": "pusher:pong", "data": [String: String]()])
        case "pusher:error", "pusher_internal:subscription_succeeded", "pusher:pong":
            break
        default:
            if let channel = frame["channel"] as? String {
                onEvent?(channel, event, payload)
            }
        }
    }

    private func sendSubscribe(_ channel: String) {
        guard let socketId else { return }
        if channel.hasPrefix("private-") {
            Task { @MainActor in
                guard let auth = try? await self.authorizer(socketId, channel) else { return }
                self.send(["event": "pusher:subscribe", "data": ["channel": channel, "auth": auth]])
            }
        } else {
            send(["event": "pusher:subscribe", "data": ["channel": channel]])
        }
    }

    private func send(_ frame: [String: Any]) {
        guard let task,
              let data = try? JSONSerialization.data(withJSONObject: frame),
              let text = String(data: data, encoding: .utf8) else { return }
        task.send(.string(text)) { _ in }
    }
}
