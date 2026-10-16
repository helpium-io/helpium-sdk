import SwiftUI

extension Color {
    /// `#RGB`, `#RRGGBB` or `#RRGGBBAA`.
    init(hex: String) {
        var raw = hex.trimmingCharacters(in: CharacterSet(charactersIn: "# "))
        if raw.count == 3 { raw = raw.map { "\($0)\($0)" }.joined() }
        var value: UInt64 = 0
        Scanner(string: raw).scanHexInt64(&value)
        let hasAlpha = raw.count == 8
        let rgb = hasAlpha ? value >> 8 : value
        self.init(
            .sRGB,
            red: Double((rgb >> 16) & 0xFF) / 255,
            green: Double((rgb >> 8) & 0xFF) / 255,
            blue: Double(rgb & 0xFF) / 255,
            opacity: hasAlpha ? Double(value & 0xFF) / 255 : 1
        )
    }

    /// White or black, whichever reads better on this color.
    static func contrasting(_ hex: String) -> Color {
        var raw = hex.trimmingCharacters(in: CharacterSet(charactersIn: "# "))
        if raw.count == 3 { raw = raw.map { "\($0)\($0)" }.joined() }
        if raw.count == 8 { raw = String(raw.prefix(6)) }
        var value: UInt64 = 0
        Scanner(string: raw).scanHexInt64(&value)
        let r = Double((value >> 16) & 0xFF), g = Double((value >> 8) & 0xFF), b = Double(value & 0xFF)
        return (0.299 * r + 0.587 * g + 0.114 * b) / 255 > 0.6 ? .black : .white
    }
}

struct Avatar: View {
    let url: String?
    let fallback: String
    var size: CGFloat = 28
    @Environment(\.palette) private var palette

    var body: some View {
        Group {
            if let url, let parsed = URL(string: url) {
                AsyncImage(url: parsed) { image in image.resizable().scaledToFill() } placeholder: { initials }
            } else {
                initials
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
    }

    private var initials: some View {
        ZStack {
            Circle().fill(palette.surface)
            Text(String(fallback.prefix(1)).uppercased())
                .font(.system(size: size * 0.45, weight: .semibold))
                .foregroundColor(palette.textSecondary)
        }
    }
}

extension View {
    /// A navigation bar in the header colors (used by sheets). iOS 16+ paints its background.
    @ViewBuilder
    func headerStyle(_ palette: Palette) -> some View {
        if #available(iOS 16.0, *) {
            self.toolbarBackground(palette.headerBackground, for: .navigationBar)
                .toolbarBackground(.visible, for: .navigationBar)
                .toolbarColorScheme(palette.headerIsDark ? .dark : .light, for: .navigationBar)
        } else {
            self
        }
    }

    /// The navigation title in the header text color.
    func headerTitle(_ title: String, _ palette: Palette) -> some View {
        toolbar {
            ToolbarItem(placement: .principal) {
                Text(title).font(.headline).foregroundColor(palette.headerText)
            }
        }
    }
}
