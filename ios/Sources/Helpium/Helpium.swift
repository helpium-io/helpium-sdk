import SwiftUI
import UIKit

/// The Helpium mobile SDK: native in-app chat with your support team and AI.
///
/// ```swift
/// Helpium.configure(widgetKey: "wk_...")
/// Helpium.identify(userJwt: tokenFromYourServer)
/// Helpium.present()
/// ```
@MainActor
public enum Helpium {
    static var store: ChatStore?
    private static var unreadListeners: [UUID: (Int) -> Void] = [:]
    private static weak var presented: UIViewController?
    private static var forcedLocale: String?

    /// Call once at launch with the workspace's widget key (Helpium > Channels > Live Chat).
    public static func configure(widgetKey: String, baseURL: URL = URL(string: "https://helpium.io")!) {
        L.locale = forcedLocale ?? preferredLocale()
        let store = ChatStore(api: APIClient(baseURL: baseURL, widgetKey: widgetKey))
        store.setLocale(forcedLocale)
        store.onUnreadCountChange = { count in unreadListeners.values.forEach { $0(count) } }
        Self.store = store
        Task { await store.start() }
    }

    /// A verified user: `userJwt` is an HS256 token your server signs with the
    /// workspace identity secret (`user_id`, `email`, `exp`). The conversation
    /// follows the user across devices and AI actions can act on their account.
    public static func identify(userJwt: String, email: String? = nil, name: String? = nil) {
        requireStore().setIdentity(.init(userJwt: userJwt, email: email, name: name))
    }

    /// An unverified user, known only by what they told your app.
    public static func identify(email: String, name: String? = nil) {
        requireStore().setIdentity(.init(email: email, name: name))
    }

    /// Call when the user signs out: stops push to this phone and hides their conversation.
    public static func logout() {
        requireStore().logout()
    }

    /// Forces the chat language (`"en"`, `"es"`). By default it follows the phone.
    public static func setLocale(_ code: String?) {
        forcedLocale = code
        L.locale = code ?? preferredLocale()
        store?.setLocale(code)
    }

    /// Replaces one of the chat's texts, e.g. `setString("Help", for: "title")`.
    public static func setString(_ value: String, for key: String) {
        L.overrides[key] = value
    }

    /// Opens the chat full screen over `viewController` (the top-most one when nil).
    public static func present(from viewController: UIViewController? = nil) {
        let store = requireStore()
        guard presented == nil, let host = viewController ?? topViewController() else { return }
        let controller = UIHostingController(rootView: HelpiumChatView(store: store, onClose: { dismiss() }))
        controller.modalPresentationStyle = .pageSheet
        presented = controller
        host.present(controller, animated: true)
    }

    /// Closes the chat if it is open.
    public static func dismiss() {
        presented?.dismiss(animated: true)
        presented = nil
    }

    /// The chat as a SwiftUI view, to push or embed instead of `present()`.
    public static func chatView() -> some View {
        HelpiumChatView(store: requireStore(), onClose: nil)
    }

    // MARK: - Unread

    /// Agent and bot messages the customer has not seen yet.
    public static var unreadCount: Int { store?.unreadCount ?? 0 }

    /// Called on the main thread whenever `unreadCount` changes. Keep the
    /// returned token; release it to stop listening.
    public static func addUnreadCountListener(_ listener: @escaping (Int) -> Void) -> HelpiumListener {
        let id = UUID()
        unreadListeners[id] = listener
        listener(unreadCount)
        return HelpiumListener { Task { @MainActor in unreadListeners[id] = nil } }
    }

    // MARK: - Push

    /// Pass the APNs device token from `didRegisterForRemoteNotificationsWithDeviceToken`.
    public static func setPushToken(_ deviceToken: Data) {
        setPushToken(deviceToken.map { String(format: "%02x", $0) }.joined())
    }

    /// The APNs device token as a hex string.
    public static func setPushToken(_ hexToken: String) {
        requireStore().setPushToken(hexToken)
    }

    /// Whether a notification was sent by Helpium.
    public static func isHelpiumPush(_ userInfo: [AnyHashable: Any]) -> Bool {
        (userInfo["helpium"] as? String) == "1" || (userInfo["helpium"] as? Int) == 1
    }

    /// Whether to show a Helpium notification that arrives in the foreground:
    /// not when the customer is already looking at the chat.
    public static func shouldShowNotification(_ userInfo: [AnyHashable: Any]) -> Bool {
        isHelpiumPush(userInfo) && store?.isVisible != true
    }

    /// Opens the conversation a tapped notification is about. Returns false
    /// when the notification is not Helpium's.
    @discardableResult
    public static func handlePush(_ userInfo: [AnyHashable: Any]) -> Bool {
        guard isHelpiumPush(userInfo), let store else { return false }
        if let token = userInfo["conversation_token"] as? String {
            Task {
                await store.open(conversationToken: token)
                present()
            }
        } else {
            present()
        }
        return true
    }

    // MARK: - Helpers

    private static func requireStore() -> ChatStore {
        guard let store else { fatalError("Call Helpium.configure(widgetKey:) before using the SDK.") }
        return store
    }

    static func preferredLocale() -> String {
        let language = Locale.preferredLanguages.first?.prefix(2).lowercased() ?? "en"
        return language == "es" ? "es" : "en"
    }

    private static func topViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive } ?? UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
        var top = scene?.windows.first { $0.isKeyWindow }?.rootViewController
        while let next = top?.presentedViewController { top = next }
        return top
    }
}

/// Keeps a listener alive; call `remove()` or let it deinit to stop it.
public final class HelpiumListener {
    private var onRemove: (() -> Void)?

    init(onRemove: @escaping () -> Void) { self.onRemove = onRemove }

    public func remove() {
        onRemove?()
        onRemove = nil
    }

    deinit { onRemove?() }
}
