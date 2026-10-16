# Helpium mobile SDKs

Native in-app support chat for iOS, Android and React Native. Customers talk to your Helpium AI and your team without leaving your app. It is the same AI, the same inbox and the same customer history as the web widget.

| Folder | What it is | Status |
|---|---|---|
| `ios/` | Swift Package `Helpium` (SwiftUI, iOS 15+, no dependencies) | v0.1 |
| `android/` | Kotlin library `io.helpium.sdk` (Android 5+, no androidx, works from Java) + Java example app | v0.1 |
| `react-native/` | `@helpium/react-native`, a bridge over both native SDKs | v0.1 |
| `flutter/` | Planned | — |
| `spec/` | The API contract every SDK implements (`API.md`) and shared test fixtures | |

## Before you start (Helpium dashboard)

1. **Widget key:** Channels → Live Chat. It identifies your workspace.
2. **Identity secret:** same page, under identity verification. Your **server** uses it to sign a short JWT for the signed-in user. Never ship the secret in the app.
3. **Push (optional):** Channels → Live Chat → Mobile apps.
   - Connect your Apple `.p8` key (Key ID, Team ID, Bundle ID).
   - Connect your Firebase service account JSON.
   - With these, agent replies reach the phone even when the app is closed.

The identity token is HS256, signed with the identity secret. Claims:
```json
{ "user_id": "42", "email": "jane@example.com", "exp": 1767225600 }
```
Include `email` in the token, or pass it to `identify`. Without an email the user is still verified when they send their first message, but their open conversation does not follow them to another device.

## iOS

**Install:** Xcode → File → Add Package Dependencies → `https://github.com/helpium-io/helpium-sdk`, product `Helpium`.

**Use:**
```swift
import Helpium

// AppDelegate / App init
Helpium.configure(widgetKey: "YOUR_WIDGET_KEY")

// After login (token signed by your server)
Helpium.identify(userJwt: token)            // add email: if the token has no email claim
// or, without verification
Helpium.identify(email: "jane@example.com", name: "Jane")

// Open the chat (UIKit or SwiftUI)
Helpium.present()                            // or Helpium.present(from: viewController)
// SwiftUI alternative: NavigationLink { Helpium.chatView() }

// Logout
Helpium.logout()

// Unread badge
let listener = Helpium.addUnreadCountListener { count in badge.text = "\(count)" }
```

**Push:**
```swift
func application(_ app: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken token: Data) {
    Helpium.setPushToken(token)
}

// UNUserNotificationCenterDelegate
func userNotificationCenter(_ c: UNUserNotificationCenter, willPresent n: UNNotification) async -> UNNotificationPresentationOptions {
    let info = n.request.content.userInfo
    if Helpium.isHelpiumPush(info) { return Helpium.shouldShowNotification(info) ? [.banner, .sound] : [] }
    return [.banner, .sound]
}

func userNotificationCenter(_ c: UNUserNotificationCenter, didReceive r: UNNotificationResponse) async {
    Helpium.handlePush(r.notification.request.content.userInfo)
}
```

## Android (Java or Kotlin)

**Install:**
```gradle
// settings.gradle (repositories)
maven { url "https://jitpack.io" }

// app/build.gradle
implementation "com.github.helpium-io:helpium-sdk:0.1.0"
```

**Use:**
```java
// Application.onCreate
Helpium.configure(this, "YOUR_WIDGET_KEY");

// After login (token signed by your server)
Helpium.identify(token);                       // or Helpium.identify(token, email, name)
// or, without verification
Helpium.identifyByEmail("jane@example.com", "Jane");

// Open the chat
Helpium.present(activity);

// Logout
Helpium.logout();

// Unread badge
Helpium.addUnreadCountListener(count -> badge.setText(String.valueOf(count)));
```

**Push.** The SDK does **not** add its own `FirebaseMessagingService`, so it never collides with yours.
```java
// Your FirebaseMessagingService
@Override public void onNewToken(String token) { Helpium.setPushToken(token); }

@Override public void onMessageReceived(RemoteMessage message) {
    if (Helpium.isHelpiumPush(message.getData())) {
        if (Helpium.shouldShowNotification(message.getData())) { /* show it with your channel */ }
        return;
    }
    // your own pushes
}

// Launcher activity: a notification tapped while the app was in the background
Helpium.handlePush(this, getIntent().getExtras());
```

Also call `Helpium.setPushToken(...)` with `FirebaseMessaging.getInstance().getToken()` at startup.

## React Native

```bash
npm install @helpium/react-native
cd ios && pod install
```
In Expo, use a development build (`npx expo run:ios`). The SDK does not run in Expo Go.

```ts
import { Helpium } from '@helpium/react-native';

Helpium.configure({ widgetKey: 'YOUR_WIDGET_KEY' });
Helpium.identify({ userJwt: token });
Helpium.present();

// Push with expo-notifications: Helpium needs the raw device token
const { data } = await Notifications.getDevicePushTokenAsync();
Helpium.setPushToken(String(data));

// Tap
if (Helpium.isHelpiumPush(response.notification.request.content.data)) {
    Helpium.handlePush(response.notification.request.content.data);
}
```

## Development

| Platform | Command |
|---|---|
| iOS | `xcodebuild test -scheme Helpium -destination 'platform=iOS Simulator,name=iPhone 16'` |
| Android | `cd android && ./gradlew :helpium:testDebugUnitTest :example:assembleDebug`. Use Android Studio's JDK: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`. Point the example at a workspace with `-Phelpium.widgetKey=... -Phelpium.baseUrl=...` |
| React Native | `cd react-native && npm run sync-native`, which copies the native sources into the package |

Every SDK implements `spec/API.md`. When the Helpium customer API changes, update the spec and the fixtures first.
