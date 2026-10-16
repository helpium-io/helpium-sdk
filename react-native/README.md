<div align="center">

<img src="https://raw.githubusercontent.com/helpium-io/helpium-sdk/main/.github/logo.svg" width="64" height="64" alt="Helpium">

# @helpium/react-native

**Native in-app support chat for React Native.**<br>
Your Helpium AI and your team, inside your app. It is built on the native Helpium iOS and Android SDKs, not a web view.

[Full documentation](https://github.com/helpium-io/helpium-sdk#5-react-native) · [Helpium](https://helpium.io) · [Issues](https://github.com/helpium-io/helpium-sdk/issues)

</div>

## Install

```bash
npm install @helpium/react-native
cd ios && pod install
```

With **Expo**, use a development build (`npx expo run:ios`, `npx expo run:android` or EAS Build). It does not run in Expo Go, and it needs no config plugin.

On iOS, add `NSCameraUsageDescription` to `Info.plist` (Expo: `ios.infoPlist` in `app.json`) so customers can attach photos from the camera.

## Use

```tsx
import { Helpium } from '@helpium/react-native';

// At startup. The widget key is in Helpium → Channels → Live Chat
Helpium.configure({ widgetKey: 'YOUR_WIDGET_KEY' });

// After login, with a token your server signs with the identity secret
Helpium.identify({ userJwt: token });

// From your Help button
Helpium.present();

// Unread badge
useEffect(() => {
  const subscription = Helpium.addUnreadCountListener(setUnread);
  return () => subscription.remove();
}, []);

// On logout
Helpium.logout();
```

## Colors

Every color of the chat is configurable, for light and dark mode. Leave any out to keep its default:

```tsx
Helpium.setAppearance({
  light: { primary: '#0ea5e9', background: '#ffffff', headerBackground: '#0ea5e9', headerText: '#ffffff' },
  dark: { primary: '#38bdf8', background: '#0f172a', surface: '#1e293b', text: '#f1f5f9' },
  colorScheme: 'system', // or 'light' / 'dark'
});
```

The colors you can set:
- `primary`, `onPrimary`
- `background`, `surface`, `text`, `textSecondary`, `border`
- `headerBackground`, `headerText`
- `customerBubble`, `customerBubbleText`, `agentBubble`, `agentBubbleText`
- `inputBackground`, `inputText`, `link`
- `unreadBadge`, `unreadBadgeText`

The [full documentation](https://github.com/helpium-io/helpium-sdk#7-customization) describes each one.

## Push notifications

Connect your Apple key and Firebase service account in Helpium (Channels → Live Chat → Mobile apps). Then pass the **raw** device token and handle taps:

```tsx
import * as Notifications from 'expo-notifications';

const { data } = await Notifications.getDevicePushTokenAsync();
Helpium.setPushToken(String(data));

Notifications.addNotificationResponseReceivedListener((response) => {
  const data = response.notification.request.content.data;
  if (Helpium.isHelpiumPush(data)) Helpium.handlePush(data);
});
```

## API

| Method | Description |
|---|---|
| `configure({ widgetKey, baseUrl?, appearance? })` | Starts the SDK |
| `setAppearance({ light?, dark?, colorScheme? })` | Every color, for light and dark mode |
| `identify({ userJwt, email?, name? })` | Verified user (token signed by your server) |
| `identifyByEmail({ email, name? })` | Unverified user |
| `logout()` | Forgets the user on this phone and stops their push |
| `present()` | Opens the native chat |
| `getUnreadCount()` · `addUnreadCountListener(cb)` | Unread agent and bot messages |
| `setPushToken(token)` | Raw APNs (iOS) or FCM (Android) token |
| `isHelpiumPush(data)` · `shouldShowNotification(data)` · `handlePush(data)` | Push handling |
| `setLocale('en' \| 'es' \| null)` | Chat language |

Server-side identity signing, push setup and troubleshooting are covered in the [full documentation](https://github.com/helpium-io/helpium-sdk#readme).

## License

MIT. Created with love by Helpium ❤️
