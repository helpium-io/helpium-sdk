package io.helpium.sdk

import android.graphics.Color

/**
 * Every color of the chat, as hex strings (`"#0ea5e9"`, `"#RRGGBBAA"` for
 * transparency). Leave one `null` to keep its default: the workspace brand
 * color for `primary`, neutral light or dark tones for the rest. Same names on
 * iOS, Android and React Native.
 *
 * Java: `new HelpiumColors().primary("#0ea5e9").background("#ffffff")`
 * Kotlin: `HelpiumColors().apply { primary = "#0ea5e9" }`
 */
class HelpiumColors {
    /** Buttons, links and the send button. Default: the workspace color from Live Chat settings. */
    var primary: String? = null
    /** Text and icons on `primary`. Default: black or white, whichever reads better. */
    var onPrimary: String? = null
    /** The screen behind everything. */
    var background: String? = null
    /** Cards, menus and secondary surfaces. */
    var surface: String? = null
    /** Main text. */
    var text: String? = null
    /** Captions, sender names, system messages and placeholders. */
    var textSecondary: String? = null
    /** Borders and dividers. */
    var border: String? = null
    /** The top bar (and the status bar behind it). Default: `background`. */
    var headerBackground: String? = null
    /** The top bar title and icons. Default: `text`. */
    var headerText: String? = null
    /** The customer's own messages. Default: `primary`. */
    var customerBubble: String? = null
    /** Text in the customer's messages. Default: `onPrimary`. */
    var customerBubbleText: String? = null
    /** Messages from the bot and agents. Default: `surface`. */
    var agentBubble: String? = null
    /** Text in bot and agent messages. Default: `text`. */
    var agentBubbleText: String? = null
    /** The message field. Default: `surface`. */
    var inputBackground: String? = null
    /** Text typed in the message field. Default: `text`. */
    var inputText: String? = null
    /** Links inside messages. Default: `primary`. */
    var link: String? = null
    /** The unread count badge. Default: red. */
    var unreadBadge: String? = null
    /** The number on the unread badge. Default: white. */
    var unreadBadgeText: String? = null

    fun primary(value: String?) = apply { primary = value }
    fun onPrimary(value: String?) = apply { onPrimary = value }
    fun background(value: String?) = apply { background = value }
    fun surface(value: String?) = apply { surface = value }
    fun text(value: String?) = apply { text = value }
    fun textSecondary(value: String?) = apply { textSecondary = value }
    fun border(value: String?) = apply { border = value }
    fun headerBackground(value: String?) = apply { headerBackground = value }
    fun headerText(value: String?) = apply { headerText = value }
    fun customerBubble(value: String?) = apply { customerBubble = value }
    fun customerBubbleText(value: String?) = apply { customerBubbleText = value }
    fun agentBubble(value: String?) = apply { agentBubble = value }
    fun agentBubbleText(value: String?) = apply { agentBubbleText = value }
    fun inputBackground(value: String?) = apply { inputBackground = value }
    fun inputText(value: String?) = apply { inputText = value }
    fun link(value: String?) = apply { link = value }
    fun unreadBadge(value: String?) = apply { unreadBadge = value }
    fun unreadBadgeText(value: String?) = apply { unreadBadgeText = value }
}

/** Light or dark: follow the phone, or force one. */
enum class HelpiumColorScheme { SYSTEM, LIGHT, DARK }

/**
 * How the chat looks: colors for light mode, colors for dark mode, and which
 * mode to use. Java: `new HelpiumAppearance().light(lightColors).dark(darkColors)`.
 */
class HelpiumAppearance @JvmOverloads constructor(
    var light: HelpiumColors = HelpiumColors(),
    var dark: HelpiumColors = HelpiumColors(),
    var colorScheme: HelpiumColorScheme = HelpiumColorScheme.SYSTEM,
) {
    fun light(colors: HelpiumColors) = apply { light = colors }
    fun dark(colors: HelpiumColors) = apply { dark = colors }
    /** The same colors in light and dark mode. */
    fun colors(colors: HelpiumColors) = apply { light = colors; dark = colors }
    fun colorScheme(scheme: HelpiumColorScheme) = apply { colorScheme = scheme }
}

/** Every color resolved for one mode: what the views paint with. */
internal data class Palette(
    val primary: Int,
    val onPrimary: Int,
    val background: Int,
    val surface: Int,
    val text: Int,
    val textSecondary: Int,
    val border: Int,
    val headerBackground: Int,
    val headerText: Int,
    val customerBubble: Int,
    val customerBubbleText: Int,
    val agentBubble: Int,
    val agentBubbleText: Int,
    val inputBackground: Int,
    val inputText: Int,
    val link: Int,
    val unreadBadge: Int,
    val unreadBadgeText: Int,
    val isDark: Boolean,
) {
    val headerIsDark get() = contrasting(headerBackground) == Color.WHITE

    companion object {
        fun resolve(appearance: HelpiumAppearance, workspacePrimary: String?, dark: Boolean): Palette {
            val custom = if (dark) appearance.dark else appearance.light
            fun pick(value: String?, fallback: String) = parse(value) ?: parse(fallback)!!

            val primary = parse(custom.primary) ?: parse(workspacePrimary) ?: parse("#6366f1")!!
            val onPrimary = parse(custom.onPrimary) ?: contrasting(primary)
            val background = pick(custom.background, if (dark) "#111827" else "#FFFFFF")
            val surface = pick(custom.surface, if (dark) "#1F2937" else "#F3F4F6")
            val text = pick(custom.text, if (dark) "#F9FAFB" else "#111827")

            return Palette(
                primary = primary,
                onPrimary = onPrimary,
                background = background,
                surface = surface,
                text = text,
                textSecondary = pick(custom.textSecondary, if (dark) "#9CA3AF" else "#6B7280"),
                border = pick(custom.border, if (dark) "#374151" else "#E5E7EB"),
                headerBackground = parse(custom.headerBackground) ?: background,
                headerText = parse(custom.headerText) ?: text,
                customerBubble = parse(custom.customerBubble) ?: primary,
                customerBubbleText = parse(custom.customerBubbleText) ?: onPrimary,
                agentBubble = parse(custom.agentBubble) ?: surface,
                agentBubbleText = parse(custom.agentBubbleText) ?: text,
                inputBackground = parse(custom.inputBackground) ?: surface,
                inputText = parse(custom.inputText) ?: text,
                link = parse(custom.link) ?: primary,
                unreadBadge = pick(custom.unreadBadge, "#EF4444"),
                unreadBadgeText = pick(custom.unreadBadgeText, "#FFFFFF"),
                isDark = dark,
            )
        }

        /** `#RGB`, `#RRGGBB` or `#RRGGBBAA` (CSS order, like the web widget). */
        fun parse(hex: String?): Int? {
            var raw = hex?.trim()?.removePrefix("#") ?: return null
            if (raw.length == 3) raw = raw.map { "$it$it" }.joinToString("")
            val value = raw.toLongOrNull(16) ?: return null
            return when (raw.length) {
                6 -> (0xFF000000 or value).toInt()
                8 -> (((value and 0xFF) shl 24) or (value shr 8)).toInt()
                else -> null
            }
        }

        /** White or black, whichever reads better on this color. */
        fun contrasting(color: Int): Int {
            val luminance = (0.299 * ((color shr 16) and 0xFF) + 0.587 * ((color shr 8) and 0xFF) + 0.114 * (color and 0xFF)) / 255
            return if (luminance > 0.6) Color.BLACK else Color.WHITE
        }
    }
}
