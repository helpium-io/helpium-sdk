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
