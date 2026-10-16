import SwiftUI

/// Every color of the chat, as hex strings (`"#0ea5e9"`). Leave one `nil` to
/// keep its default: the workspace brand color for `primary`, and neutral
/// light or dark tones for the rest. Same names on iOS, Android and React Native.
public struct HelpiumColors: Equatable {
    /// Buttons, links and the send button. Default: the workspace color from Live Chat settings.
    public var primary: String?
    /// Text and icons on `primary`. Default: black or white, whichever reads better.
    public var onPrimary: String?
    /// The screen behind everything.
    public var background: String?
    /// Cards, menus and secondary surfaces.
    public var surface: String?
    /// Main text.
    public var text: String?
    /// Captions, timestamps, sender names, system messages and placeholders.
    public var textSecondary: String?
    /// Borders and dividers.
    public var border: String?
    /// The top bar. Default: `background`.
    public var headerBackground: String?
    /// The top bar title and icons. Default: `text`.
    public var headerText: String?
    /// The customer's own messages. Default: `primary`.
    public var customerBubble: String?
    /// Text in the customer's messages. Default: `onPrimary`.
    public var customerBubbleText: String?
    /// Messages from the bot and agents. Default: `surface`.
    public var agentBubble: String?
    /// Text in bot and agent messages. Default: `text`.
    public var agentBubbleText: String?
    /// The message field. Default: `surface`.
    public var inputBackground: String?
    /// Text typed in the message field. Default: `text`.
    public var inputText: String?
    /// Links inside messages. Default: `primary`.
    public var link: String?
    /// The unread count badge. Default: red.
    public var unreadBadge: String?
    /// The number on the unread badge. Default: white.
    public var unreadBadgeText: String?

    public init(
        primary: String? = nil, onPrimary: String? = nil, background: String? = nil, surface: String? = nil,
        text: String? = nil, textSecondary: String? = nil, border: String? = nil,
        headerBackground: String? = nil, headerText: String? = nil,
        customerBubble: String? = nil, customerBubbleText: String? = nil,
        agentBubble: String? = nil, agentBubbleText: String? = nil,
        inputBackground: String? = nil, inputText: String? = nil, link: String? = nil,
        unreadBadge: String? = nil, unreadBadgeText: String? = nil
    ) {
        self.primary = primary; self.onPrimary = onPrimary; self.background = background; self.surface = surface
        self.text = text; self.textSecondary = textSecondary; self.border = border
        self.headerBackground = headerBackground; self.headerText = headerText
        self.customerBubble = customerBubble; self.customerBubbleText = customerBubbleText
        self.agentBubble = agentBubble; self.agentBubbleText = agentBubbleText
        self.inputBackground = inputBackground; self.inputText = inputText; self.link = link
        self.unreadBadge = unreadBadge; self.unreadBadgeText = unreadBadgeText
    }
}

/// Light or dark: follow the phone, or force one.
public enum HelpiumColorScheme: String {
    case system, light, dark
}

/// How the chat looks: colors for light mode, colors for dark mode, and which mode to use.
public struct HelpiumAppearance: Equatable {
    public var light: HelpiumColors
    public var dark: HelpiumColors
    public var colorScheme: HelpiumColorScheme

    public init(light: HelpiumColors = .init(), dark: HelpiumColors = .init(), colorScheme: HelpiumColorScheme = .system) {
        self.light = light
        self.dark = dark
        self.colorScheme = colorScheme
    }

    /// The same colors in light and dark mode.
    public init(colors: HelpiumColors, colorScheme: HelpiumColorScheme = .system) {
        self.init(light: colors, dark: colors, colorScheme: colorScheme)
    }
}

/// Every color resolved for one mode: what the views paint with.
struct Palette: Equatable {
    var primary, onPrimary, background, surface, text, textSecondary, border: Color
    var headerBackground, headerText: Color
    var customerBubble, customerBubbleText, agentBubble, agentBubbleText: Color
    var inputBackground, inputText, link, unreadBadge, unreadBadgeText: Color
    var isDark: Bool
    var headerIsDark: Bool

    static func resolve(_ appearance: HelpiumAppearance, workspacePrimary: String?, dark: Bool) -> Palette {
        let custom = dark ? appearance.dark : appearance.light
        let primaryHex = custom.primary ?? workspacePrimary ?? "#6366f1"
        func pick(_ value: String?, _ fallback: String) -> Color { Color(hex: value ?? fallback) }

        let primary = Color(hex: primaryHex)
        let onPrimary = custom.onPrimary.map(Color.init(hex:)) ?? Color.contrasting(primaryHex)
        let background = pick(custom.background, dark ? "#111827" : "#FFFFFF")
        let surface = pick(custom.surface, dark ? "#1F2937" : "#F3F4F6")
        let text = pick(custom.text, dark ? "#F9FAFB" : "#111827")
        let headerHex = custom.headerBackground ?? custom.background ?? (dark ? "#111827" : "#FFFFFF")

        return Palette(
            primary: primary,
            onPrimary: onPrimary,
            background: background,
            surface: surface,
            text: text,
            textSecondary: pick(custom.textSecondary, dark ? "#9CA3AF" : "#6B7280"),
            border: pick(custom.border, dark ? "#374151" : "#E5E7EB"),
            headerBackground: custom.headerBackground.map(Color.init(hex:)) ?? background,
            headerText: custom.headerText.map(Color.init(hex:)) ?? text,
            customerBubble: custom.customerBubble.map(Color.init(hex:)) ?? primary,
            customerBubbleText: custom.customerBubbleText.map(Color.init(hex:)) ?? onPrimary,
            agentBubble: custom.agentBubble.map(Color.init(hex:)) ?? surface,
            agentBubbleText: custom.agentBubbleText.map(Color.init(hex:)) ?? text,
            inputBackground: custom.inputBackground.map(Color.init(hex:)) ?? surface,
            inputText: custom.inputText.map(Color.init(hex:)) ?? text,
            link: custom.link.map(Color.init(hex:)) ?? primary,
            unreadBadge: pick(custom.unreadBadge, "#EF4444"),
            unreadBadgeText: pick(custom.unreadBadgeText, "#FFFFFF"),
            isDark: dark,
            headerIsDark: Color.contrasting(headerHex) == .white
        )
    }
}

private struct PaletteKey: EnvironmentKey {
    static let defaultValue = Palette.resolve(HelpiumAppearance(), workspacePrimary: nil, dark: false)
}

extension EnvironmentValues {
    var palette: Palette {
        get { self[PaletteKey.self] }
        set { self[PaletteKey.self] = newValue }
    }
}
