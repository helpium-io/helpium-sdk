import Foundation
import Security

/// Remembers the conversation token across launches in the Keychain, scoped
/// per widget key and per signed-in user, so a shared phone never shows one
/// user's conversation to another.
struct Storage {
    private let service = "io.helpium.sdk"

    func conversationToken(widgetKey: String, userKey: String?) -> String? {
        read(account: account(widgetKey, userKey))
    }

    func setConversationToken(_ token: String?, widgetKey: String, userKey: String?) {
        let account = account(widgetKey, userKey)
        delete(account: account)
        if let token { write(token, account: account) }
    }

    private func account(_ widgetKey: String, _ userKey: String?) -> String {
        "conversation:\(widgetKey):\(userKey ?? "anonymous")"
    }

    private func read(account: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess, let data = item as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    private func write(_ value: String, account: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecValueData as String: Data(value.utf8),
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock,
        ]
        SecItemAdd(query as CFDictionary, nil)
    }

    private func delete(account: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        SecItemDelete(query as CFDictionary)
    }
}

enum HelpiumInfo {
    static let version = "0.1.0"
}
