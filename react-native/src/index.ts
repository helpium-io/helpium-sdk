import { NativeEventEmitter, NativeModules, Platform } from 'react-native';

type NativeHelpium = {
    configure(widgetKey: string, baseUrl: string | null): void;
    setAppearance(appearance: HelpiumAppearance): void;
    identify(userJwt: string, email: string | null, name: string | null): void;
    identifyByEmail(email: string, name: string | null): void;
    logout(): void;
    setLocale(locale: string | null): void;
    present(): void;
    setPushToken(token: string): void;
    handlePush(data: Record<string, string>): Promise<boolean>;
    shouldShowNotification(data: Record<string, string>): Promise<boolean>;
    getUnreadCount(): Promise<number>;
};

const native: NativeHelpium | undefined = NativeModules.HelpiumReactNative;

function module(): NativeHelpium {
    if (native == null) {
        throw new Error(
            `@helpium/react-native is not linked. Rebuild the app (${Platform.OS === 'ios' ? 'pod install' : 'gradle sync'}); it does not run in Expo Go.`,
        );
    }

    return native;
}

const emitter = native != null ? new NativeEventEmitter(NativeModules.HelpiumReactNative) : null;

/** Push payloads arrive with every value as a string. */
function stringify(data: Record<string, unknown> | null | undefined): Record<string, string> {
    const out: Record<string, string> = {};
    for (const [key, value] of Object.entries(data ?? {})) {
        if (value != null) {
            out[key] = typeof value === 'string' ? value : JSON.stringify(value);
        }
    }

    return out;
}

/**
 * Every color of the chat, as hex strings ('#0ea5e9', '#RRGGBBAA' for
 * transparency). Leave one out to keep its default: the workspace brand color
 * for `primary`, neutral light or dark tones for the rest.
 */
export type HelpiumColors = {
    /** Buttons, links and the send button. Default: the workspace color. */
    primary?: string;
    /** Text and icons on `primary`. Default: black or white, whichever reads better. */
    onPrimary?: string;
    /** The screen behind everything. */
    background?: string;
    /** Cards, menus and secondary surfaces. */
    surface?: string;
    /** Main text. */
    text?: string;
    /** Captions, sender names, system messages and placeholders. */
    textSecondary?: string;
    /** Borders and dividers. */
    border?: string;
    /** The top bar. Default: `background`. */
    headerBackground?: string;
    /** The top bar title and icons. Default: `text`. */
    headerText?: string;
    /** The customer's own messages. Default: `primary`. */
    customerBubble?: string;
    /** Text in the customer's messages. Default: `onPrimary`. */
    customerBubbleText?: string;
    /** Messages from the bot and agents. Default: `surface`. */
    agentBubble?: string;
    /** Text in bot and agent messages. Default: `text`. */
    agentBubbleText?: string;
    /** The message field. Default: `surface`. */
    inputBackground?: string;
    /** Text typed in the message field. Default: `text`. */
    inputText?: string;
    /** Links inside messages. Default: `primary`. */
    link?: string;
    /** The unread count badge. Default: red. */
    unreadBadge?: string;
    /** The number on the unread badge. Default: white. */
    unreadBadgeText?: string;
};

export type HelpiumAppearance = {
    /** Colors in light mode. */
    light?: HelpiumColors;
    /** Colors in dark mode. */
    dark?: HelpiumColors;
    /** Follow the phone (default) or force one mode. */
    colorScheme?: 'system' | 'light' | 'dark';
};

export type HelpiumConfig = {
    /** The workspace widget key, from Helpium > Channels > Live Chat. */
    widgetKey: string;
    /** Defaults to https://helpium.io. */
    baseUrl?: string;
    /** Every color of the chat, for light and dark mode. */
    appearance?: HelpiumAppearance;
};

export const Helpium = {
    /** Call once at startup. */
    configure({ widgetKey, baseUrl, appearance }: HelpiumConfig): void {
        if (appearance) {
            module().setAppearance(appearance);
        }
        module().configure(widgetKey, baseUrl ?? null);
    },

    /**
     * Every color of the chat, for light and dark mode. Applies right away,
     * even while the chat is open.
     *
     *     Helpium.setAppearance({
     *       light: { primary: '#0ea5e9', background: '#ffffff', agentBubble: '#f1f5f9' },
     *       dark: { primary: '#38bdf8', background: '#0f172a', agentBubble: '#1e293b' },
     *     });
     */
    setAppearance(appearance: HelpiumAppearance): void {
        module().setAppearance(appearance);
    },

    /**
     * A verified user. `userJwt` is an HS256 token your server signs with the
     * workspace identity secret (`user_id`, `email`, `exp`). Pass `email` when
     * the token has no `email` claim.
     */
    identify(user: { userJwt: string; email?: string; name?: string }): void {
        module().identify(user.userJwt, user.email ?? null, user.name ?? null);
    },

    /** An unverified user, known only by what they told your app. */
    identifyByEmail(user: { email: string; name?: string }): void {
        module().identifyByEmail(user.email, user.name ?? null);
    },

    /** Stops push to this phone and hides the user's conversation. */
    logout(): void {
        module().logout();
    },

    /** Forces the chat language ('en' | 'es'); null follows the phone. */
    setLocale(locale: string | null): void {
        module().setLocale(locale);
    },

    /** Opens the native chat. */
    present(): void {
        module().present();
    },

    /** The raw device token: APNs hex on iOS, FCM on Android (expo: getDevicePushTokenAsync().data). */
    setPushToken(token: string): void {
        module().setPushToken(token);
    },

    /** Whether a notification's data was sent by Helpium. */
    isHelpiumPush(data: Record<string, unknown> | null | undefined): boolean {
        return String(data?.helpium ?? '') === '1';
    },

    /** Whether to show a Helpium notification that arrives in the foreground (not while the chat is open). */
    shouldShowNotification(data: Record<string, unknown> | null | undefined): Promise<boolean> {
        return module().shouldShowNotification(stringify(data));
    },

    /** Opens the conversation a tapped notification is about. Resolves false when it is not Helpium's. */
    handlePush(data: Record<string, unknown> | null | undefined): Promise<boolean> {
        return module().handlePush(stringify(data));
    },

    /** Agent and bot messages the customer has not seen yet. */
    getUnreadCount(): Promise<number> {
        return module().getUnreadCount();
    },

    /** Called whenever the unread count changes. */
    addUnreadCountListener(listener: (count: number) => void): { remove(): void } {
        if (emitter == null) {
            return { remove() {} };
        }

        const subscription = emitter.addListener('HelpiumUnreadCountChanged', (event: { count: number }) => listener(event.count));

        return { remove: () => subscription.remove() };
    },
};

export default Helpium;
