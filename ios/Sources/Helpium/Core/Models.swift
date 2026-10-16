import Foundation

struct WidgetSettings: Decodable, Equatable {
    struct Websocket: Decodable, Equatable {
        let host: String
        let port: Int
        let scheme: String
        let appKey: String
        let useApiHost: Bool?
    }

    struct InstantAnswer: Decodable, Equatable, Identifiable {
        let id: Int
        let label: String
        let isShortcut: Bool
    }

    let enableChat: Bool
    let enableBot: Bool
    let requireCustomerDetails: Bool
    let collectMarketingOptIn: Bool
    let humanHandoffMode: String
    let aiReplyMode: String
    let botName: String
    let botAvatarUrl: String?
    let primaryColor: String
    let isOnline: Bool
    let offlineMessage: String?
    let conversationRatingsEnabled: Bool
    let aiAvailable: Bool
    let websocket: Websocket
    let instantAnswers: [InstantAnswer]
}

struct Agent: Decodable, Equatable {
    let id: Int
    let name: String
    let avatarUrl: String?
}

struct ConversationDTO: Decodable {
    let id: Int
    let status: String
    let assignedAgent: Agent?
    let closedReason: String?
    let rated: Bool
    let customerIdentified: Bool
    let messages: [MessageDTO]?
    let widgetToken: String?
}

struct IdentifyResult: Decodable {
    let verified: Bool
    let activeConversation: ConversationDTO?
}

struct Attachment: Decodable, Equatable, Hashable {
    let name: String?
    let url: String
    let type: String?

    var isImage: Bool { type?.hasPrefix("image/") ?? false }
}

struct Source: Decodable, Equatable, Hashable {
    let title: String
    let url: String?
}

enum Interactive: Equatable {
    case retry
    case confirm
    case suggestions([String])

    init?(json: [String: Any]?) {
        switch json?["type"] as? String {
        case "retry": self = .retry
        case "confirm": self = .confirm
        case "suggestions": self = .suggestions((json?["options"] as? [String]) ?? [])
        default: return nil
        }
    }
}

enum Role: String {
    case customer, bot, agent, system

    /// REST says `user` for the customer and `agent` for a human; the socket
    /// says `customer` and `user`.
    init(rest: String) {
        switch rest {
        case "user": self = .customer
        case "assistant": self = .bot
        case "system": self = .system
        default: self = .agent
        }
    }

    init(senderType: String) {
        switch senderType {
        case "customer": self = .customer
        case "bot": self = .bot
        case "system": self = .system
        default: self = .agent
        }
    }
}

/// One message as the chat shows it, built from a REST resource, a socket
/// event, or an optimistic send.
struct ChatMessage: Identifiable, Equatable {
    /// Server id; negative for an optimistic message not confirmed yet.
    var id: Int
    var role: Role
    var content: String
    var createdAt: String
    var deliveredAt: String?
    var seenAt: String?
    var senderName: String?
    var senderAvatarUrl: String?
    var shouldHandoff: Bool
    var feedback: String?
    var sources: [Source]
    var attachments: [Attachment]
    var interactive: Interactive?
    var localImage: Data?

    var isPending: Bool { id < 0 }
    var isFromTeam: Bool { role == .agent || role == .bot }
}

struct MessageDTO: Decodable {
    let id: Int
    let content: String?
    let role: String
    let createdAt: String
    let deliveredAt: String?
    let seenAt: String?
    let senderName: String?
    let senderAvatarUrl: String?
    let shouldHandoff: Bool?
    let customerFeedback: String?
    let aiSources: [Source]?
    let attachments: [Attachment]?
    let interactive: JSONValue?

    var message: ChatMessage {
        ChatMessage(
            id: id,
            role: Role(rest: role),
            content: content ?? "",
            createdAt: createdAt,
            deliveredAt: deliveredAt,
            seenAt: seenAt,
            senderName: senderName,
            senderAvatarUrl: senderAvatarUrl,
            shouldHandoff: shouldHandoff ?? false,
            feedback: customerFeedback,
            sources: aiSources ?? [],
            attachments: attachments ?? [],
            interactive: Interactive(json: interactive?.object)
        )
    }
}

/// The `App\Events\NewMessage` socket payload.
struct NewMessageEvent: Decodable {
    let id: Int
    let senderType: String
    let senderName: String?
    let senderAvatarUrl: String?
    let content: String?
    let contentTruncated: Bool?
    let isInternal: Bool?
    let attachments: [Attachment]?
    let createdAt: String
    let deliveredAt: String?
    let seenAt: String?
    let shouldHandoff: Bool?
    let aiSources: [Source]?
    let interactive: JSONValue?

    var message: ChatMessage {
        let role = Role(senderType: senderType)
        return ChatMessage(
            id: id,
            role: role,
            content: content ?? "",
            createdAt: createdAt,
            deliveredAt: deliveredAt,
            seenAt: seenAt,
            senderName: role == .agent ? senderName : nil,
            senderAvatarUrl: senderAvatarUrl,
            shouldHandoff: shouldHandoff ?? false,
            feedback: nil,
            sources: aiSources ?? [],
            attachments: attachments ?? [],
            interactive: Interactive(json: interactive?.object)
        )
    }
}

struct ConversationUpdatedEvent: Decodable {
    let status: String?
    let assignedUser: Agent?
    let reason: String?
}

struct MessageStatusEvent: Decodable {
    let id: Int
    let deliveredAt: String?
    let seenAt: String?
}

struct BotTypingEvent: Decodable {
    let isTyping: Bool
    let status: String?
}

struct InteractiveUpdatedEvent: Decodable {
    let id: Int
    let interactive: JSONValue?
}

struct TypingWhisper: Decodable {
    let senderType: String?
    let isTyping: Bool
}

/// Untyped JSON, for the free-form `interactive` column.
enum JSONValue: Decodable {
    case value(Any)

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if container.decodeNil() { self = .value(NSNull()); return }
        if let v = try? container.decode([String: JSONValue].self) { self = .value(v.mapValues(\.raw)); return }
        if let v = try? container.decode([JSONValue].self) { self = .value(v.map(\.raw)); return }
        if let v = try? container.decode(Bool.self) { self = .value(v); return }
        if let v = try? container.decode(Double.self) { self = .value(v); return }
        if let v = try? container.decode(String.self) { self = .value(v); return }
        self = .value(NSNull())
    }

    var raw: Any { if case .value(let v) = self { return v }; return NSNull() }
    var object: [String: Any]? { raw as? [String: Any] }
}
