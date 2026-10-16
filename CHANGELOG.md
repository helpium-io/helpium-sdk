# Changelog

All notable changes to the Helpium mobile SDKs. Versions follow [Semantic Versioning](https://semver.org).

## 0.1.0 - 2026-10-16

First release.

### Added
- **iOS SDK**: Swift Package with a native SwiftUI chat (iOS 15+, no dependencies), usable from UIKit and SwiftUI.
- **Android SDK**: native chat built with plain Android views (Android 5.0+, no androidx), with a Java-friendly API. Published through JitPack.
- **React Native**: `@helpium/react-native` bridge over both native SDKs.
- Identity verification with a server-signed HS256 JWT. The conversation follows the user across devices.
- Push notifications for agent replies through the workspace's own APNs and FCM credentials.
- Realtime messages, bot and agent typing indicators, read receipts, image attachments, human handoff, ratings and answer feedback.
- Every color of the chat is configurable, for light and dark mode, with an optional forced mode (`setAppearance`).
- English and Spanish texts, all overridable.
