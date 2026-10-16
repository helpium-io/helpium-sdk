<div align="center">

<img src="https://raw.githubusercontent.com/helpium-io/helpium-sdk/main/.github/logo.svg" width="72" height="72" alt="Helpium">

# Helpium for React Native

**Native in-app support chat, with your AI agent and your team, in a few lines of code.**

[![npm](https://img.shields.io/npm/v/@helpium/react-native/beta?label=npm&color=4f46e5)](https://www.npmjs.com/package/@helpium/react-native)
[![license](https://img.shields.io/badge/license-MIT-4f46e5)](https://github.com/helpium-io/helpium-sdk/blob/main/LICENSE)
![platforms](https://img.shields.io/badge/platforms-iOS%2015%2B%20%7C%20Android%205%2B-4f46e5)
![expo](https://img.shields.io/badge/Expo-development%20builds-4f46e5)

[Website](https://helpium.io) · [Developers](https://helpium.io/developers) · [Full documentation](https://github.com/helpium-io/helpium-sdk#readme) · [Report an issue](https://github.com/helpium-io/helpium-sdk/issues)

<br>

<img src="https://raw.githubusercontent.com/helpium-io/helpium-sdk/main/.github/screenshots/ios-welcome.jpg" width="200" alt="The chat ready for the first message">&nbsp;
<img src="https://raw.githubusercontent.com/helpium-io/helpium-sdk/main/.github/screenshots/ios-conversation.jpg" width="200" alt="A conversation with AI and agent replies">&nbsp;
<img src="https://raw.githubusercontent.com/helpium-io/helpium-sdk/main/.github/screenshots/android-conversation.jpg" width="196" alt="The same chat on Android">&nbsp;
<img src="https://raw.githubusercontent.com/helpium-io/helpium-sdk/main/.github/screenshots/ios-midnight.jpg" width="200" alt="A custom dark theme">

</div>

<br>

`@helpium/react-native` puts the [Helpium](https://helpium.io) support chat inside your app. It wraps the native Helpium iOS and Android SDKs, so your users get a real native screen, not a web view. Your AI agent answers in seconds, your team takes over from the Helpium inbox when needed, and every conversation lands next to your web chat, email and WhatsApp.

- **Native UI** - SwiftUI on iOS, native views on Android, opened with one call.
- **Your AI agent included** - the same knowledge, procedures and AI actions you set up in Helpium.
- **Human handoff** - "Talk to a human" sends the conversation to your inbox, with replies in real time.
- **Verified users** - a token signed by your server, so the conversation follows the user across devices.
- **Push notifications** - agent replies reach users with the app closed, from your own Apple and Firebase accounts.
- **Your brand** - every color for light and dark mode, and every text in every language.
- **Unread badge** - a listener for the count of replies the user hasn't seen.
- **TypeScript** - typed API, no JavaScript dependencies.

> [!NOTE]
> This is a beta. The API is stable, but tell us about anything rough in [issues](https://github.com/helpium-io/helpium-sdk/issues).

## Contents

- [Requirements](#requirements)
- [Installation](#installation)
- [Quick start](#quick-start)
- [Identify your users](#identify-your-users)
- [Unread badge](#unread-badge)
- [Push notifications](#push-notifications)
- [Colors](#colors)
- [Language and texts](#language-and-texts)
- [API reference](#api-reference)
- [Troubleshooting](#troubleshooting)

## Requirements

| | Minimum |
|---|---|
| React Native | 0.71 |
| iOS | 15.0 |
| Android | 5.0 (API 21) |
| Expo | A [development build](https://docs.expo.dev/develop/development-builds/introduction/). Expo Go is not supported. |
| Helpium | A workspace with the Live Chat channel ([start free](https://helpium.io)) |

## Installation

```bash
npm install @helpium/react-native
# or
yarn add @helpium/react-native
```

**Bare React Native**: install the iOS pod.

```bash
cd ios && pod install
```

**Expo**: no config plugin is needed. Rebuild your development build:

```bash
npx expo run:ios
npx expo run:android
# or with EAS
eas build --profile development
```

**Camera access (iOS)**: so users can attach photos from the camera, add a usage description to `Info.plist`. With Expo, in `app.json`:

```json
{
  "expo": {
    "ios": {
      "infoPlist": {
        "NSCameraUsageDescription": "Take a photo to send to our support team."
      }
    }
  }
}
```

## Quick start

You need your **widget key**. In Helpium, open **Settings → Live Chat** and copy it from **Installation**. It is public and is meant to ship inside your app.

```tsx
import { Helpium } from '@helpium/react-native';

// 1. Once, at startup
Helpium.configure({ widgetKey: 'YOUR_WIDGET_KEY' });

// 2. From your Help button
<Button title="Help" onPress={() => Helpium.present()} />
```

That's it: the chat opens with your brand color, your greeting and your AI agent. Users who aren't identified are asked for their name and email before the first message.

## Identify your users

When a user is signed in to your app, tell Helpium who they are. The chat then skips the name and email form, the conversation follows them to any device, and AI actions can safely act on their account.

**1. On your server**, sign a short token with the **identity secret** (Settings → Live Chat → Customer identification). It is a standard HS256 JWT:

| Claim | | |
|---|---|---|
| `user_id` | required, or `email` | Your id for the user, as a string |
| `email` | recommended | Lets the conversation follow the user to another device |
| `exp` | required | Expiry, in Unix seconds. An hour is plenty. |

```js
// Node.js, for example (any language with a JWT library works the same)
const jwt = require('jsonwebtoken');

app.get('/api/helpium-token', requireLogin, (req, res) => {
  const token = jwt.sign(
    { user_id: String(req.user.id), email: req.user.email },
    process.env.HELPIUM_IDENTITY_SECRET,
    { algorithm: 'HS256', expiresIn: '1h' }
  );
  res.json({ token });
});
```

Python, PHP, Ruby and Java versions are in the [identity verification guide](https://docs.helpium.io/help-center/identity-verification).

> [!WARNING]
> The identity secret stays on your server. Never put it in your app, your repository or a web page: anyone who has it can sign in as any of your users.

**2. In your app**, after login:

```tsx
const { token } = await api.get('/api/helpium-token');
Helpium.identify({ userJwt: token });
```

**3. On logout**, so the next user on this phone starts clean and the previous one stops getting push:

```tsx
Helpium.logout();
```

No login in your app? Skip all of this, or pass what the user told you with `Helpium.identifyByEmail({ email, name })` (treated as unverified).

## Unread badge

```tsx
import { useEffect, useState } from 'react';
import { Helpium } from '@helpium/react-native';

export function useHelpiumUnread() {
  const [unread, setUnread] = useState(0);

  useEffect(() => {
    Helpium.getUnreadCount().then(setUnread);
    const subscription = Helpium.addUnreadCountListener(setUnread);
    return () => subscription.remove();
  }, []);

  return unread;
}
```

The count covers agent and bot replies the user hasn't seen, and drops to zero when they open the chat.

## Push notifications

Push notifications are sent from **your own** Apple and Firebase accounts, so they come from your app and you keep the keys.

**1. Connect your credentials in Helpium**: Settings → Live Chat → **Mobile apps**.
- **iOS**: an APNs key (`.p8`) from your Apple Developer account, with its Key ID, your Team ID and your Bundle ID.
- **Android**: the service account JSON of your Firebase project.

**2. Pass the raw device token and the taps to Helpium.** With [`expo-notifications`](https://docs.expo.dev/versions/latest/sdk/notifications/), which also works in bare React Native:

```tsx
import * as Notifications from 'expo-notifications';
import { Helpium } from '@helpium/react-native';

// The raw APNs / FCM token, not the Expo push token
const { data } = await Notifications.getDevicePushTokenAsync();
Helpium.setPushToken(String(data));

// Don't show a Helpium notification while the user is already in the chat
Notifications.setNotificationHandler({
  handleNotification: async (notification) => {
    const data = notification.request.content.data;
    const show = Helpium.isHelpiumPush(data) ? await Helpium.shouldShowNotification(data) : true;
    return { shouldShowBanner: show, shouldShowList: show, shouldPlaySound: show, shouldSetBadge: false };
  },
});

// Open the conversation when the user taps one
Notifications.addNotificationResponseReceivedListener((response) => {
  const data = response.notification.request.content.data;
  if (Helpium.isHelpiumPush(data)) {
    Helpium.handlePush(data);
  }
});
```

Helpium never registers its own notification service, so it works next to the notifications your app already sends. The phone registers for push once the user has a conversation.

## Colors

By default the chat uses the brand color from your Live Chat settings, with light and dark mode following the phone. Every color can also be set from the app. Leave any out to keep its default:

```tsx
Helpium.configure({
  widgetKey: 'YOUR_WIDGET_KEY',
  appearance: {
    light: { primary: '#0ea5e9', headerBackground: '#0ea5e9', headerText: '#ffffff' },
    dark: { primary: '#a78bfa', background: '#0f0d1a', surface: '#1c1830' },
    colorScheme: 'system', // or 'light' / 'dark' to force one
  },
});

// or later, applied right away even while the chat is open
Helpium.setAppearance({ dark: { primary: '#38bdf8' } });
```

| Color | Used for | Default |
|---|---|---|
| `primary` | Buttons, links, the send button | Your workspace color |
| `onPrimary` | Text and icons on `primary` | Black or white, whichever reads better |
| `background` | The screen | Light or dark neutral |
| `surface` | Cards, menus, secondary surfaces | Light or dark neutral |
| `text` / `textSecondary` | Main text / captions, names, placeholders | Light or dark neutral |
| `border` | Borders and dividers | Light or dark neutral |
| `headerBackground` / `headerText` | The top bar | `background` / `text` |
| `customerBubble` / `customerBubbleText` | The user's own messages | `primary` / `onPrimary` |
| `agentBubble` / `agentBubbleText` | Bot and agent messages | `surface` / `text` |
| `inputBackground` / `inputText` | The message field | `surface` / `text` |
| `link` | Links inside messages | `primary` |
| `unreadBadge` / `unreadBadgeText` | The unread count | Red / white |

Colors are hex strings: `#RRGGBB`, or `#RRGGBBAA` for transparency.

## Language and texts

The chat follows the phone language, with English and Spanish built in. To force one:

```tsx
Helpium.setLocale('es'); // null follows the phone again
```

Every text can be replaced per language, the greeting and the welcome text included, and you can add languages Helpium doesn't ship. Because the chat is native, the texts are native resources with the same keys on both platforms:

```xml
<!-- Android: android/app/src/main/res/values-pt/strings.xml -->
<string name="helpium_greeting">Olá! Precisa de ajuda com o seu pedido?</string>
```

```
/* iOS: pt.lproj/Localizable.strings in your app target */
"helpium_greeting" = "Olá! Precisa de ajuda com o seu pedido?";
```

The most common keys are `greeting`, `welcome_title`, `welcome_subtitle`, `placeholder`, `reply_time` and `talk_to_human`. The [full list](https://github.com/helpium-io/helpium-sdk#language-and-texts) is in the main documentation.

## API reference

```ts
import { Helpium } from '@helpium/react-native';
```

| Method | Description |
|---|---|
| `configure({ widgetKey, baseUrl?, appearance? })` | Starts the SDK. Call once, at startup. |
| `identify({ userJwt, email?, name? })` | A verified user, with the token your server signed. |
| `identifyByEmail({ email, name? })` | An unverified user, known only by what they told your app. |
| `logout()` | Forgets the user on this phone and stops their push. |
| `present()` | Opens the chat. |
| `setAppearance({ light?, dark?, colorScheme? })` | Changes the colors, right away. |
| `setLocale(locale \| null)` | Forces the chat language; `null` follows the phone. |
| `getUnreadCount(): Promise<number>` | Replies the user hasn't seen. |
| `addUnreadCountListener(listener): { remove() }` | Called whenever the unread count changes. |
| `setPushToken(token)` | The raw APNs (iOS) or FCM (Android) device token. |
| `isHelpiumPush(data): boolean` | Whether a notification was sent by Helpium. |
| `shouldShowNotification(data): Promise<boolean>` | Whether to show one that arrives in the foreground (false while the chat is open). |
| `handlePush(data): Promise<boolean>` | Opens the conversation of a tapped notification; `false` if it isn't Helpium's. |

`HelpiumColors`, `HelpiumAppearance` and `HelpiumConfig` are exported as types.

## Troubleshooting

<details>
<summary><b>"@helpium/react-native is not linked"</b></summary>

The native code isn't in your build. Run `pod install` and rebuild, or rebuild your Expo development build. Expo Go can't load native modules.
</details>

<details>
<summary><b>The chat says it is not available</b></summary>

The widget key is wrong, or the workspace has no active plan.
</details>

<details>
<summary><b>Signed-in users still see the name and email form</b></summary>

`identify` wasn't called, or the token expired or was signed with another workspace's secret. The identity secret is different in each Helpium workspace.
</details>

<details>
<summary><b>The user gets a new conversation on every device</b></summary>

The token has no `email` claim and `identify` got no `email`. Add one of them.
</details>

<details>
<summary><b>No push notifications</b></summary>

1. The credentials are connected in Settings → Live Chat → Mobile apps, for the right Bundle ID or Firebase project.
2. iOS: the Push Notifications capability is on, and the APNs environment matches the build (Sandbox for development builds).
3. Android 13+: the `POST_NOTIFICATIONS` permission is granted.
4. `setPushToken` gets the **raw** device token, not the Expo push token.
5. The user has sent at least one message.
</details>

<details>
<summary><b>Tapping a notification opens the app but not the chat</b></summary>

Call `Helpium.handlePush(data)` with the tapped notification's data, as in [Push notifications](#push-notifications).
</details>

## More

- [Full documentation](https://github.com/helpium-io/helpium-sdk#readme): native iOS and Android, push setup in detail, how it works
- [Helpium for developers](https://helpium.io/developers)
- [Changelog](https://github.com/helpium-io/helpium-sdk/blob/main/CHANGELOG.md)

## License

MIT © Helpium

Created with love by Helpium ❤️
