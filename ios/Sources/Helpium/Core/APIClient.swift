import Foundation

public struct HelpiumError: Error, LocalizedError {
    public let status: Int
    public let code: String?
    public let message: String?

    public var errorDescription: String? { message ?? code ?? "HTTP \(status)" }
}

/// The customer chat API (spec/API.md), authenticated with the widget key.
final class APIClient {
    let baseURL: URL
    let widgetKey: String
    private let session: URLSession

    static let decoder: JSONDecoder = {
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        return decoder
    }()

    init(baseURL: URL, widgetKey: String, session: URLSession = .shared) {
        self.baseURL = baseURL
        self.widgetKey = widgetKey
        self.session = session
    }

    private struct Wrapped<T: Decodable>: Decodable { let data: T }
    private struct OptionalWrapped<T: Decodable>: Decodable { let data: T? }

    func settings(locale: String) async throws -> WidgetSettings {
        try await request("GET", "widget/settings", query: ["lang": locale], as: Wrapped<WidgetSettings>.self).data
    }

    func conversation(token: String) async throws -> ConversationDTO? {
        try await request("GET", "conversations/\(token)", as: OptionalWrapped<ConversationDTO>.self).data
    }

    func createConversation(_ body: [String: Any]) async throws -> ConversationDTO {
        try await request("POST", "conversations", body: body, as: Wrapped<ConversationDTO>.self).data
    }

    func sendMessage(token: String, _ body: [String: Any]) async throws -> [MessageDTO] {
        try await request("POST", "conversations/\(token)/messages", body: body, as: Wrapped<[MessageDTO]>.self).data
    }

    func messages(token: String, since: String? = nil) async throws -> [MessageDTO] {
        try await request("GET", "conversations/\(token)/messages", query: since.map { ["since": $0] } ?? [:], as: Wrapped<[MessageDTO]>.self).data
    }

    func requestHuman(token: String) async throws -> ConversationDTO {
        try await request("POST", "conversations/\(token)/request-human", as: Wrapped<ConversationDTO>.self).data
    }

    func close(token: String) async throws -> ConversationDTO {
        try await request("POST", "conversations/\(token)/close", as: Wrapped<ConversationDTO>.self).data
    }

    func rate(token: String, rating: Int, comment: String?) async throws {
        var body: [String: Any] = ["rating": rating]
        if let comment, !comment.isEmpty { body["comment"] = comment }
        try await send("POST", "conversations/\(token)/rate", body: body)
    }

    func feedback(token: String, messageId: Int, value: String) async throws {
        try await send("POST", "conversations/\(token)/messages/\(messageId)/feedback", body: ["feedback": value])
    }

    func retry(token: String, messageId: Int) async throws {
        try await send("POST", "conversations/\(token)/messages/\(messageId)/retry")
    }

    func updateStatus(token: String, status: String, ids: [Int]) async throws {
        try await send("POST", "conversations/\(token)/messages/status", body: ["status": status, "message_ids": ids])
    }

    func identify(_ body: [String: Any]) async throws -> IdentifyResult {
        try await request("POST", "tracking/identify", body: body, as: Wrapped<IdentifyResult>.self).data
    }

    func broadcastingAuth(socketId: String, channel: String) async throws -> String {
        struct Auth: Decodable { let auth: String }
        return try await request("POST", "broadcasting/auth", body: ["socket_id": socketId, "channel_name": channel], as: Auth.self).auth
    }

    func registerDevice(_ body: [String: Any]) async throws {
        try await send("POST", "devices", body: body)
    }

    func unregisterDevice(pushToken: String) async throws {
        try await send("DELETE", "devices", body: ["push_token": pushToken])
    }

    // MARK: - Transport

    private func request<T: Decodable>(_ method: String, _ path: String, query: [String: String] = [:], body: [String: Any]? = nil, as type: T.Type) async throws -> T {
        let data = try await send(method, path, query: query, body: body)
        return try Self.decoder.decode(T.self, from: data)
    }

    @discardableResult
    private func send(_ method: String, _ path: String, query: [String: String] = [:], body: [String: Any]? = nil) async throws -> Data {
        var components = URLComponents(url: baseURL.appendingPathComponent("api/customer/\(path)"), resolvingAgainstBaseURL: false)!
        if !query.isEmpty { components.queryItems = query.map { URLQueryItem(name: $0.key, value: $0.value) } }

        var request = URLRequest(url: components.url!)
        request.httpMethod = method
        request.setValue(widgetKey, forHTTPHeaderField: "X-API-Key")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if let body {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONSerialization.data(withJSONObject: body)
        }

        let (data, response) = try await session.data(for: request)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0

        guard (200..<300).contains(status) else {
            let error = (try? JSONSerialization.jsonObject(with: data) as? [String: Any])?["error"] as? [String: Any]
            throw HelpiumError(status: status, code: error?["code"] as? String, message: error?["message"] as? String)
        }

        return data
    }
}
