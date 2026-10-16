<div align="center">

<img src=".github/logo.svg" width="72" height="72" alt="Helpium">

# Helpium Mobile SDKs

**Native in-app support chat for iOS, Android and React Native.**<br>
Your Helpium AI and your team, inside your app.

[![Version](https://img.shields.io/github/v/tag/helpium-io/helpium-sdk?label=version&color=6366f1)](https://github.com/helpium-io/helpium-sdk/tags)
[![iOS 15+](https://img.shields.io/badge/iOS-15%2B-000000?logo=apple&logoColor=white)](#3-ios)
[![Android 5.0+](https://img.shields.io/badge/Android-5.0%2B-3DDC84?logo=android&logoColor=white)](#4-android)
[![React Native](https://img.shields.io/badge/React%20Native-0.71%2B-61DAFB?logo=react&logoColor=black)](#5-react-native)
[![Swift Package Manager](https://img.shields.io/badge/SPM-compatible-F05138?logo=swift&logoColor=white)](#3-ios)
[![JitPack](https://jitpack.io/v/helpium-io/helpium-sdk.svg)](https://jitpack.io/#helpium-io/helpium-sdk)

[Website](https://helpium.io) · [Developers](https://helpium.io/developers) · [API contract](spec/API.md) · [Report an issue](https://github.com/helpium-io/helpium-sdk/issues)

</div>

---

## Quick start

```swift
// iOS
import Helpium

Helpium.configure(widgetKey: "YOUR_WIDGET_KEY")   // at launch
Helpium.identify(userJwt: tokenFromYourServer)    // after login
Helpium.present()                                 // from your Help button
```

```java
// Android (Java or Kotlin)
Helpium.configure(this, "YOUR_WIDGET_KEY");       // Application.onCreate
Helpium.identify(tokenFromYourServer);            // after login
Helpium.present(activity);                        // from your Help button
```

That's it. The widget key is in your Helpium dashboard under **Channels → Live Chat**.

---

## Features

- 💬 **Native UI.** SwiftUI on iOS and native views on Android, not a web view. It follows your brand color and light or dark mode.
- 🤖 **AI first.** Your bot answers instantly from your articles, procedures and AI actions. It hands over to a person when the customer asks or the AI is not sure.
- ⚡ **Realtime.** Agent replies, typing indicators and read receipts arrive live.
- 🔐 **Identity verification.** A token signed by your server makes the conversation follow the user across devices, and lets AI actions act on their account safely.
- 🔔 **Push notifications.** Agent replies reach the phone even with the app closed. They're sent from your own Apple and Firebase accounts.
- 🖼️ **Images.** Customers can send photos from their library or camera.
- ⭐ **Ratings and feedback.** Customers rate closed conversations and give a thumbs up or down to bot answers.
- 🌍 **Localized.** English and Spanish are built in, and every text can be overridden.
- 🪶 **Lightweight.**
  - iOS has no dependencies.
  - Android needs only OkHttp and the Kotlin standard library, with no androidx, AppCompat or Compose.

Conversations from your app land in the same Helpium inbox as web chat, email and WhatsApp, with the same customer history.

| Platform | Package | Requirements |
|---|---|---|
| iOS | Swift Package `Helpium` | iOS 15+, Xcode 15+ |
| Android | `com.github.helpium-io:helpium-sdk` | Android 5.0 (API 21)+, Java 8+ or Kotlin |
| React Native | `@helpium/react-native` | React Native 0.71+ (new and old architecture) |

---

## Contents

1. [Set up your workspace](#1-set-up-your-workspace)
2. [Sign the user identity on your server](#2-sign-the-user-identity-on-your-server)
3. [iOS](#3-ios)
4. [Android](#4-android)
5. [React Native](#5-react-native)
6. [Push notifications setup](#6-push-notifications-setup)
7. [Customization](#7-customization)
8. [Troubleshooting](#8-troubleshooting)
9. [How it works](#9-how-it-works)
10. [Contributing and releasing](#10-contributing-and-releasing)

---

## 1. Set up your workspace

In the Helpium dashboard, open **Channels → Live Chat**:

1. **Copy the widget key.** It points the SDK at your workspace and is safe to ship inside your app.
2. **Copy the identity secret.** Use it only on your server, never inside the app ([section 2](#2-sign-the-user-identity-on-your-server)).
3. *(Optional)* In the **Mobile apps** section, connect your Apple key and Firebase service account to send push notifications ([section 6](#6-push-notifications-setup)).

The rest of that page applies to the SDK just like the web widget:
- bot on or off
- AI reply mode
- human handoff
- ratings
- pre-chat form
- brand color
- bot name and avatar

Changes take effect without shipping an app update.

---

## 2. Sign the user identity on your server

When a user is logged in to your app, your **backend** signs a short JWT with the identity secret and your app passes it to the SDK. With it, Helpium:

- keeps one conversation per user, following them to any phone or the web
- skips the pre-chat form
- lets AI actions (orders, bookings, account changes) act on the right account

The token is an **HS256 JWT**:

| Claim | Required | Description |
|---|---|---|
| `user_id` | yes (or `email`) | Your user's id, as a string |
| `email` | recommended | Needed for the conversation to follow the user to another device |
| `exp` | yes | Expiry (Unix timestamp). One hour to one day is typical |

Expose an endpoint such as `GET /api/helpium-token` for your logged-in users and return the token.

<details open>
<summary><b>PHP</b></summary>

```php
use Firebase\JWT\JWT;

$userJwt = JWT::encode([
    'user_id' => (string) $user->id,
    'email' => $user->email,
    'exp' => time() + 3600,
], env('HELPIUM_IDENTITY_SECRET'), 'HS256');
```
</details>

<details>
<summary><b>Node.js</b></summary>

```js
const jwt = require('jsonwebtoken');

const userJwt = jwt.sign(
  { user_id: String(user.id), email: user.email },
  process.env.HELPIUM_IDENTITY_SECRET,
  { algorithm: 'HS256', expiresIn: '1h' }
);
```
</details>

<details>
<summary><b>Python</b></summary>

```python
import jwt, os, time

user_jwt = jwt.encode(
    {"user_id": str(user.id), "email": user.email, "exp": int(time.time()) + 3600},
    os.environ["HELPIUM_IDENTITY_SECRET"],
    algorithm="HS256",
)
```
</details>

<details>
<summary><b>Ruby</b></summary>

```ruby
user_jwt = JWT.encode(
  { user_id: user.id.to_s, email: user.email, exp: Time.now.to_i + 3600 },
  ENV['HELPIUM_IDENTITY_SECRET'],
  'HS256'
)
```
</details>

<details>
<summary><b>Java</b></summary>

```java
String userJwt = Jwts.builder()
    .claim("user_id", String.valueOf(user.getId()))
    .claim("email", user.getEmail())
    .setExpiration(new Date(System.currentTimeMillis() + 3_600_000))
    .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
    .compact();
```
</details>

> [!WARNING]
> Never put the identity secret in your app. Anyone can extract it from the binary and impersonate any of your users.

**No login in your app?** Skip this. The SDK asks for name and email before the first message (when the pre-chat form is on), or you can call `identify` with an email you already know (unverified).

---

## 3. iOS

### Installation

**Xcode:** File → Add Package Dependencies…
1. Enter `https://github.com/helpium-io/helpium-sdk`.
2. Choose the rule "Up to Next Major" from `0.1.0`.
3. Add the **Helpium** product to your app target.

**Package.swift:**
```swift
dependencies: [
    .package(url: "https://github.com/helpium-io/helpium-sdk", from: "0.1.0"),
],
targets: [
    .target(name: "MyApp", dependencies: [.product(name: "Helpium", package: "helpium-sdk")]),
]
```

### Info.plist

Customers can attach photos from the camera. Add a usage description, or iOS terminates the app when the camera opens:

```xml
<key>NSCameraUsageDescription</key>
<string>Take a photo to send to our support team.</string>
```

Picking from the photo library needs no permission.

### Configure

Call `configure` once, as early as possible.

<details open>
<summary><b>UIKit</b></summary>

```swift
import Helpium

func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
    Helpium.configure(widgetKey: "YOUR_WIDGET_KEY")
    return true
}
```
</details>

<details>
<summary><b>SwiftUI</b></summary>

```swift
import Helpium

@main
struct MyApp: App {
    init() { Helpium.configure(widgetKey: "YOUR_WIDGET_KEY") }
    var body: some Scene { WindowGroup { ContentView() } }
}
```
</details>

### Identify the user

```swift
// After login, with the token from your server
Helpium.identify(userJwt: token)

// If the token has no email claim, pass it so the conversation follows the user across devices
Helpium.identify(userJwt: token, email: user.email, name: user.name)

// Without verification: an email you already know
Helpium.identify(email: "jane@example.com", name: "Jane")

// On logout: hides the user's conversation and stops push to this phone
Helpium.logout()
```

### Open the chat

```swift
Helpium.present()               // over the top-most screen
Helpium.present(from: self)     // over a specific view controller
Helpium.dismiss()               // close it from code

// SwiftUI: push or embed it like any view
NavigationLink("Help") { Helpium.chatView() }
```

### Unread badge

```swift
private var unread: HelpiumListener?

unread = Helpium.addUnreadCountListener { [weak self] count in
    self?.badge.isHidden = count == 0
    self?.badge.text = "\(count)"
}

let count = Helpium.unreadCount   // or read it once
```

Keep the returned `HelpiumListener`. It stops listening when released or when you call `remove()`.

### Push notifications

Connect your Apple key in the dashboard first ([section 6](#6-push-notifications-setup)), and enable the **Push Notifications** capability in Xcode.

```swift
import Helpium
import UserNotifications

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        Helpium.configure(widgetKey: "YOUR_WIDGET_KEY")
        UNUserNotificationCenter.current().delegate = self
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound]) { granted, _ in
            if granted { DispatchQueue.main.async { application.registerForRemoteNotifications() } }
        }
        return true
    }

    // 1. Give Helpium the device token
    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        Helpium.setPushToken(deviceToken)
    }

    // 2. Foreground: show it unless the customer is already looking at the chat
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        let info = notification.request.content.userInfo
        if Helpium.isHelpiumPush(info) {
            return await Helpium.shouldShowNotification(info) ? [.banner, .sound, .badge] : []
        }
        return [.banner, .sound, .badge] // your own notifications
    }

    // 3. Tapped: open the conversation
    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        let info = response.notification.request.content.userInfo
        if await Helpium.handlePush(info) { return }
        // your own notifications
    }
}
```

The phone registers once the user has a conversation and re-registers automatically. Calling `setPushToken` on every launch is fine.

### API reference

| API | Description |
|---|---|
| `configure(widgetKey:baseURL:)` | Starts the SDK. `baseURL` defaults to `https://helpium.io` |
| `identify(userJwt:email:name:)` | Verified user (token signed by your server) |
| `identify(email:name:)` | Unverified user |
| `logout()` | Forgets the user on this phone and stops their push |
| `present(from:)` · `dismiss()` | Opens or closes the chat full screen |
| `chatView()` | The chat as a SwiftUI view |
| `unreadCount` · `addUnreadCountListener(_:)` | Unread agent and bot messages |
| `setPushToken(_:)` | APNs device token (`Data` or hex `String`) |
| `isHelpiumPush(_:)` · `shouldShowNotification(_:)` · `handlePush(_:)` | Push handling |
| `setLocale(_:)` | `"en"`, `"es"`, or `nil` to follow the phone |
| `setString(_:for:)` | Overrides one text ([Customization](#7-customization)) |

---

## 4. Android

Written in Kotlin and equally comfortable from **Java**. It uses plain Android views, with no androidx, AppCompat or Compose, so it fits old and new projects alike.

### Installation

Add JitPack to your repositories in `settings.gradle` (or the root `build.gradle` in older projects):

```gradle
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url "https://jitpack.io" }
    }
}
```

Add the dependency to `app/build.gradle`:

```gradle
dependencies {
    implementation "com.github.helpium-io:helpium-sdk:0.1.0"
}
```

<details>
<summary>Kotlin DSL</summary>

```kotlin
maven("https://jitpack.io")

implementation("com.github.helpium-io:helpium-sdk:0.1.0")
```
</details>

The SDK's manifest adds the `INTERNET` permission and its chat activity. ProGuard and R8 rules ship with the library.

### Configure

<details open>
<summary><b>Java</b></summary>

```java
public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Helpium.configure(this, "YOUR_WIDGET_KEY");
    }
}
```
</details>

<details>
<summary><b>Kotlin</b></summary>

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Helpium.configure(this, "YOUR_WIDGET_KEY")
    }
}
```
</details>

Register it with `android:name=".MyApplication"` on `<application>` in your manifest.

### Identify the user

```java
Helpium.identify(token);                                  // after login
Helpium.identify(token, user.getEmail(), user.getName()); // if the token has no email claim
Helpium.identifyByEmail("jane@example.com", "Jane");      // without verification
Helpium.logout();                                         // on logout
```

### Open the chat

```java
helpButton.setOnClickListener(view -> Helpium.present(this));
```

The chat opens in its own activity. Back returns to your app.

### Unread badge

```java
private final Helpium.UnreadCountListener unreadListener = count -> {
    badge.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
    badge.setText(String.valueOf(count));
};

@Override protected void onStart() {
    super.onStart();
    Helpium.addUnreadCountListener(unreadListener);
}

@Override protected void onStop() {
    Helpium.removeUnreadCountListener(unreadListener);
    super.onStop();
}
```

### Push notifications

Connect your Firebase service account in the dashboard first ([section 6](#6-push-notifications-setup)), and add Firebase Cloud Messaging to your app.

The SDK does **not** register its own `FirebaseMessagingService`, so it never conflicts with yours. You forward the token and the messages:

```java
public class MyMessagingService extends FirebaseMessagingService {
    @Override
    public void onNewToken(@NonNull String token) {
        Helpium.setPushToken(token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        Map<String, String> data = message.getData();

        if (Helpium.isHelpiumPush(data)) {
            // App in the foreground: show it unless the chat is already open
            if (Helpium.shouldShowNotification(data) && message.getNotification() != null) {
                showNotification(message.getNotification().getTitle(), message.getNotification().getBody(), data);
            }
            return;
        }

        // your own notifications
    }

    private void showNotification(String title, String body, Map<String, String> data) {
        Intent intent = new Intent(this, MainActivity.class);
        for (Map.Entry<String, String> entry : data.entrySet()) intent.putExtra(entry.getKey(), entry.getValue());
        PendingIntent tap = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, "support")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build();

        NotificationManagerCompat.from(this).notify(data.get("conversation_token").hashCode(), notification);
    }
}
```

Pass the current token at startup too:

```java
FirebaseMessaging.getInstance().getToken().addOnSuccessListener(Helpium::setPushToken);
```

When the app is **in the background**, Android shows the notification itself, and a tap opens your launcher activity with the push data in its extras. Hand them to Helpium:

```java
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Helpium.handlePush(this, getIntent().getExtras());
}

@Override
protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    Helpium.handlePush(this, intent.getExtras());
}
```

On Android 13+ request the `POST_NOTIFICATIONS` runtime permission, as for any notification.

### API reference

| API | Description |
|---|---|
| `configure(context, widgetKey[, baseUrl])` | Starts the SDK |
| `identify(userJwt[, email, name])` | Verified user |
| `identifyByEmail(email[, name])` | Unverified user |
| `logout()` | Forgets the user on this phone and stops their push |
| `present(context)` | Opens the chat |
| `getUnreadCount()` · `addUnreadCountListener` · `removeUnreadCountListener` | Unread agent and bot messages |
| `setPushToken(token)` | FCM registration token |
| `isHelpiumPush(data \| extras)` · `shouldShowNotification(data)` · `handlePush(context, data \| extras)` | Push handling |
| `setLocale(code)` | `"en"`, `"es"`, or `null` to follow the phone. Call before `configure` |

---

## 5. React Native

> [!NOTE]
> The npm package is on its way. Until it's published, React Native apps can't install it from npm.

```bash
npm install @helpium/react-native
cd ios && pod install
```

With **Expo**, use a development build (`npx expo run:ios`, `npx expo run:android` or EAS Build). It does not run in Expo Go, and it needs no config plugin.

Add `NSCameraUsageDescription` to your iOS `Info.plist` (or `ios.infoPlist` in `app.json`).

```tsx
import { Helpium } from '@helpium/react-native';

Helpium.configure({ widgetKey: 'YOUR_WIDGET_KEY' });     // at startup
Helpium.identify({ userJwt: token });                     // after login (optional email and name)
Helpium.identifyByEmail({ email: 'jane@example.com' });   // or unverified

<Button title="Help" onPress={() => Helpium.present()} />

useEffect(() => {
  const subscription = Helpium.addUnreadCountListener(setUnread);
  return () => subscription.remove();
}, []);

Helpium.logout();                                         // on logout
```

### Push with expo-notifications

```tsx
import * as Notifications from 'expo-notifications';
import { Helpium } from '@helpium/react-native';

// Helpium needs the raw APNs/FCM token, not the Expo push token
const { data } = await Notifications.getDevicePushTokenAsync();
Helpium.setPushToken(String(data));

// Foreground
Notifications.setNotificationHandler({
  handleNotification: async (notification) => {
    const data = notification.request.content.data;
    const show = Helpium.isHelpiumPush(data) ? await Helpium.shouldShowNotification(data) : true;
    return { shouldShowBanner: show, shouldShowList: show, shouldPlaySound: show, shouldSetBadge: false };
  },
});

// Tapped
Notifications.addNotificationResponseReceivedListener((response) => {
  const data = response.notification.request.content.data;
  if (Helpium.isHelpiumPush(data)) Helpium.handlePush(data);
});
```

With `@react-native-firebase/messaging`, pass `messaging().getToken()` (Android) or `messaging().getAPNSToken()` (iOS) to `setPushToken`.

---

## 6. Push notifications setup

Helpium sends the push from **your** Apple and Firebase accounts, so notifications come from your app and you keep control of the keys.

### Apple (APNs)

1. Open [developer.apple.com](https://developer.apple.com/account/resources/authkeys/list) → Certificates, Identifiers & Profiles → **Keys** → **+**.
2. Name the key, check **Apple Push Notifications service (APNs)**, then Continue and Register.
3. Download the `.p8` file (Apple lets you download it only once) and note the **Key ID**.
4. Your **Team ID** is in Membership details, top right of the developer portal.
5. In Helpium, go to **Channels → Live Chat → Mobile apps → iOS → Connect**. Upload the `.p8` and enter the Key ID, Team ID and your app's **Bundle ID**.
6. Choose the environment:
   - **Production** for TestFlight and App Store builds.
   - **Sandbox** only for builds you run from Xcode.

### Firebase (FCM)

1. In the [Firebase console](https://console.firebase.google.com), open your project → ⚙️ Project settings → **Service accounts**.
2. Click **Generate new private key** to download the JSON.
3. In Helpium, go to **Channels → Live Chat → Mobile apps → Android → Connect** and upload the JSON.

The Mobile apps section shows how many phones are registered on each platform.

### Payload

| Field | Value |
|---|---|
| Title | The agent's name |
| Body | The reply, up to 180 characters |
| `helpium` | `"1"`, what `isHelpiumPush` checks |
| `conversation_token` | The conversation to open |
| `message_id` | The reply |

Only **human agent** replies are pushed. Bot answers arrive while the customer is in the chat, and internal notes never leave the inbox. If the reply is still unseen after 5 minutes, the email fallback (a Live Chat setting) sends it as well.

---

## 7. Customization

**Colors, bot name and avatar** come from your Live Chat settings, so you can change them without an app update.

**Language.** The chat follows the phone language (English and Spanish included). To force one:

```swift
Helpium.setLocale("es")   // iOS
```
```java
Helpium.setLocale("es");  // Android, before configure
```

**Texts.** Override any text.

iOS:
```swift
Helpium.setString("Need help?", for: "welcome_title")
```

Android: define a string resource with the same name in your app. Your value wins.
```xml
<!-- res/values/strings.xml -->
<string name="helpium_welcome_title">Need help?</string>
<!-- res/values-es/strings.xml -->
<string name="helpium_welcome_title">¿Necesitas ayuda?</string>
```

| Key (Android: `helpium_` + key) | Default |
|---|---|
| `title` | Support |
| `welcome_title` | Hi there 👋 |
| `welcome_subtitle` | Ask us anything. We usually answer in a few minutes. |
| `new_conversation` | Send us a message |
| `continue_conversation` | Continue your conversation |
| `placeholder` | Write a message... |
| `talk_to_human` | Talk to a human |
| `offline` | We are away right now. Leave a message and we will get back to you. |
| `prechat_title` | Before we start |
| `rate_title` | How was your conversation? |

The full list is in [`Strings.swift`](ios/Sources/Helpium/Core/Strings.swift) and [`strings.xml`](android/helpium/src/main/res/values/strings.xml).

---

## 8. Troubleshooting

<details>
<summary><b>iOS crash: "Call Helpium.configure(widgetKey:) before using the SDK"</b></summary>

`configure` must run before any other call. Put it at app launch.
</details>

<details>
<summary><b>The chat says it is not available</b></summary>

Either the widget key is wrong, or the workspace has no active plan.
</details>

<details>
<summary><b>The user gets a new conversation on every device</b></summary>

The token has no `email` claim and `identify` received no email. Add one of them.
</details>

<details>
<summary><b>The pre-chat form appears for logged-in users</b></summary>

`identify` was not called, or the token expired or was signed with another workspace's secret. The SDK then treats the user as an unverified visitor.
</details>

<details>
<summary><b>No push on iOS</b></summary>

Check, in order:
1. the `.p8` is connected with the right Bundle ID
2. the environment matches the build (Sandbox for Xcode builds)
3. the Push Notifications capability is on
4. `setPushToken` is called
5. the user has sent at least one message (the phone registers once a conversation exists)
</details>

<details>
<summary><b>No push on Android</b></summary>

Check, in order:
1. the service account belongs to the same Firebase project as your `google-services.json`
2. `setPushToken` is called
3. `POST_NOTIFICATIONS` is granted on Android 13+
</details>

<details>
<summary><b>Tapping the notification opens the app but not the chat</b></summary>

Call `handlePush` with the tap data:
- iOS: in `didReceive`
- Android: in the launcher activity's `onCreate` and `onNewIntent`
</details>

<details>
<summary><b>React Native: "@helpium/react-native is not linked"</b></summary>

Rebuild the native app. Expo Go is not supported.
</details>

---

## 9. How it works

- The SDK talks to the Helpium customer API (`/api/customer`) with your widget key, and to Helpium's realtime server over a WebSocket (Pusher protocol).
- A conversation is created with the first message. Its token is stored on the phone, scoped per user: in the Keychain on iOS, in private preferences on Android.
- If the connection drops, the SDK catches up from the API as soon as it reconnects.
- Every SDK implements the same contract: [`spec/API.md`](spec/API.md).

```
helpium-sdk/
├── Package.swift          Swift Package Manager entry
├── ios/                   iOS SDK (Sources, Tests)
├── android/               Android SDK (helpium) + Java example app (example)
├── react-native/          @helpium/react-native bridge
└── spec/                  API contract and shared test fixtures
```

---

## 10. Contributing and releasing

Run the tests:

```bash
# iOS (from the repository root)
xcodebuild test -scheme Helpium -destination 'platform=iOS Simulator,name=iPhone 16'

# Android (JDK 17+, Android Studio's bundled JDK works)
cd android && ./gradlew :helpium:testDebugUnitTest :example:assembleDebug

# React Native: copy the native sources into the package
cd react-native && npm run sync-native
```

The Java example app reads `-Phelpium.widgetKey=...` and `-Phelpium.baseUrl=...`.

When the Helpium customer API changes, update `spec/API.md` and `spec/fixtures` first. Both native test suites read the same fixtures.

**Release:**
1. Bump the version in three places:
   - `HelpiumInfo.version` (iOS)
   - `SDK_VERSION` (both Android `build.gradle` files)
   - `react-native/package.json`
2. Add the changes to [CHANGELOG.md](CHANGELOG.md).
3. Tag and push: `git tag 0.2.0 && git push origin 0.2.0`. Swift Package Manager and JitPack pick up the tag.

---

<div align="center">

Created with love by Helpium ❤️

</div>
