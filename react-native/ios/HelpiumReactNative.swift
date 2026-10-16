import Foundation
import React

/// React Native bridge over the Helpium iOS SDK (compiled into this pod).
@objc(HelpiumReactNative)
final class HelpiumReactNative: RCTEventEmitter {
    private var unreadListener: HelpiumListener?
    private var hasListeners = false

    override static func requiresMainQueueSetup() -> Bool { true }

    override func supportedEvents() -> [String]! { ["HelpiumUnreadCountChanged"] }

    override func startObserving() { hasListeners = true }

    override func stopObserving() { hasListeners = false }

    private func onMain(_ work: @escaping @MainActor () -> Void) {
        Task { @MainActor in work() }
    }

    @objc func configure(_ widgetKey: String, baseUrl: String?) {
        onMain {
            if let baseUrl, let url = URL(string: baseUrl) {
                Helpium.configure(widgetKey: widgetKey, baseURL: url)
            } else {
                Helpium.configure(widgetKey: widgetKey)
            }
            self.unreadListener = Helpium.addUnreadCountListener { [weak self] count in
                guard let self, self.hasListeners else { return }
                self.sendEvent(withName: "HelpiumUnreadCountChanged", body: ["count": count])
            }
        }
    }

    @objc func setAppearance(_ appearance: NSDictionary) {
        let value = Self.appearance(from: appearance)
        onMain { Helpium.setAppearance(value) }
    }

    static func appearance(from raw: NSDictionary) -> HelpiumAppearance {
        func colors(_ key: String) -> HelpiumColors {
            let c = raw[key] as? [String: Any] ?? [:]
            func v(_ name: String) -> String? { c[name] as? String }
            return HelpiumColors(
                primary: v("primary"), onPrimary: v("onPrimary"), background: v("background"), surface: v("surface"),
                text: v("text"), textSecondary: v("textSecondary"), border: v("border"),
                headerBackground: v("headerBackground"), headerText: v("headerText"),
                customerBubble: v("customerBubble"), customerBubbleText: v("customerBubbleText"),
                agentBubble: v("agentBubble"), agentBubbleText: v("agentBubbleText"),
                inputBackground: v("inputBackground"), inputText: v("inputText"), link: v("link"),
                unreadBadge: v("unreadBadge"), unreadBadgeText: v("unreadBadgeText")
            )
        }
        let scheme = HelpiumColorScheme(rawValue: raw["colorScheme"] as? String ?? "system") ?? .system
        return HelpiumAppearance(light: colors("light"), dark: colors("dark"), colorScheme: scheme)
    }

    @objc func identify(_ userJwt: String, email: String?, name: String?) {
        onMain { Helpium.identify(userJwt: userJwt, email: email, name: name) }
    }

    @objc func identifyByEmail(_ email: String, name: String?) {
        onMain { Helpium.identify(email: email, name: name) }
    }

    @objc func logout() {
        onMain { Helpium.logout() }
    }

    @objc func setLocale(_ locale: String?) {
        onMain { Helpium.setLocale(locale) }
    }

    @objc func present() {
        onMain { Helpium.present() }
    }

    @objc func setPushToken(_ token: String) {
        onMain { Helpium.setPushToken(token) }
    }

    @objc func handlePush(_ data: NSDictionary, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        let userInfo = data as? [AnyHashable: Any] ?? [:]
        onMain { resolve(Helpium.handlePush(userInfo)) }
    }

    @objc func shouldShowNotification(_ data: NSDictionary, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        let userInfo = data as? [AnyHashable: Any] ?? [:]
        onMain { resolve(Helpium.shouldShowNotification(userInfo)) }
    }

    @objc func getUnreadCount(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        onMain { resolve(Helpium.unreadCount) }
    }
}
