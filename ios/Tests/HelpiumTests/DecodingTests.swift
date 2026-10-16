import XCTest
@testable import Helpium

final class DecodingTests: XCTestCase {
    private func fixture(_ name: String) throws -> Data {
        let url = try XCTUnwrap(Bundle.module.url(forResource: name, withExtension: "json", subdirectory: "Fixtures"))
        return try Data(contentsOf: url)
    }

    private struct Wrapped<T: Decodable>: Decodable { let data: T }

    func testDecodesWidgetSettings() throws {
        let settings = try APIClient.decoder.decode(Wrapped<WidgetSettings>.self, from: fixture("settings")).data
        XCTAssertEqual(settings.botName, "Orbi")
        XCTAssertEqual(settings.websocket.appKey, "abc")
        XCTAssertEqual(settings.instantAnswers.first?.id, 12)
        XCTAssertTrue(settings.conversationRatingsEnabled)
    }

    func testDecodesConversationWithRolesAndInteractive() throws {
        let conversation = try APIClient.decoder.decode(Wrapped<ConversationDTO>.self, from: fixture("conversation")).data
        let messages = try XCTUnwrap(conversation.messages).map(\.message)

        XCTAssertEqual(conversation.assignedAgent?.name, "Ana")
        XCTAssertEqual(messages[0].role, .customer)
        XCTAssertEqual(messages[1].role, .bot)
        XCTAssertTrue(messages[1].shouldHandoff)
        XCTAssertEqual(messages[1].interactive, .suggestions(["Yes", "No"]))
        XCTAssertEqual(messages[1].attachments.first?.isImage, true)
        XCTAssertEqual(messages[1].sources.first?.title, "Shipping")
    }

    func testSocketSenderTypeUserIsAnAgent() throws {
        let event = try APIClient.decoder.decode(NewMessageEvent.self, from: fixture("new_message_event"))
        XCTAssertEqual(event.message.role, .agent)
        XCTAssertEqual(event.message.senderName, "Ana")
    }

    func testReadsClaimsFromTheIdentityToken() {
        // {"user_id":42,"email":"jane@example.com"} as base64url, unsigned.
        let identity = ChatStore.Identity(userJwt: "eyJhbGciOiJIUzI1NiJ9.eyJ1c2VyX2lkIjo0MiwiZW1haWwiOiJqYW5lQGV4YW1wbGUuY29tIn0.sig")
        XCTAssertEqual(identity.jwtClaim("user_id"), "42")
        XCTAssertEqual(identity.jwtClaim("email"), "jane@example.com")
        XCTAssertEqual(identity.storageKey, "42")
    }
}

@MainActor
final class ChatStoreEventTests: XCTestCase {
    private func store() -> ChatStore {
        ChatStore(api: APIClient(baseURL: URL(string: "https://helpium.test")!, widgetKey: "wk"))
    }

    private func event(_ json: String) -> Data { Data(json.utf8) }

    func testAnAgentMessageClearsTypingAndCountsAsUnread() {
        let store = store()
        store.handle(event: "App\\Events\\BotTyping", data: event(#"{"conversation_id":1,"is_typing":true,"status":null}"#))
        XCTAssertTrue(store.isBotTyping)

        store.handle(event: "App\\Events\\NewMessage", data: event(#"{"id":9,"sender_type":"user","sender_name":"Ana","content":"Hi","created_at":"2026-10-16T12:00:00Z"}"#))

        XCTAssertFalse(store.isBotTyping)
        XCTAssertEqual(store.messages.count, 1)
        XCTAssertEqual(store.unreadCount, 1)
    }

    func testInternalNotesAreDropped() {
        let store = store()
        store.handle(event: "App\\Events\\NewMessage", data: event(#"{"id":9,"sender_type":"user","content":"secret","is_internal":true,"created_at":"2026-10-16T12:00:00Z"}"#))
        XCTAssertTrue(store.messages.isEmpty)
    }

    func testPendingStatusMeansHandoffRequested() {
        let store = store()
        store.handle(event: "App\\Events\\ConversationUpdated", data: event(#"{"id":1,"update_type":"handoff_requested","status":"pending","assigned_user":null}"#))
        XCTAssertTrue(store.handoffRequested)
    }

    func testAutoCloseKeepsTheInactivityReason() {
        let store = store()
        store.handle(event: "App\\Events\\ConversationUpdated", data: event(#"{"id":1,"update_type":"status_changed","status":"closed","reason":"inactivity"}"#))
        XCTAssertTrue(store.isClosed)
        XCTAssertEqual(store.closedReason, "inactivity")
    }

    func testAgentTypingWhisperIsShownButCustomerOnesAreNot() {
        let store = store()
        store.handle(event: "client-typing", data: event(#"{"sender_type":"customer","is_typing":true}"#))
        XCTAssertFalse(store.isAgentTyping)
        store.handle(event: "client-typing", data: event(#"{"sender_type":"agent","sender_name":null,"is_typing":true}"#))
        XCTAssertTrue(store.isAgentTyping)
    }
}
