import Foundation

enum TimeLabel {
    private static let withFraction: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f
    }()

    private static let plain = ISO8601DateFormatter()

    static func date(_ iso: String) -> Date? {
        withFraction.date(from: iso) ?? plain.date(from: iso)
    }

    /// "Just now", "6:50 PM" today, "Oct 14, 6:50 PM" before.
    static func short(_ iso: String) -> String {
        guard let date = date(iso) else { return "" }
        if Date().timeIntervalSince(date) < 60 { return L.t("just_now") }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: L.locale)
        if Calendar.current.isDateInToday(date) {
            formatter.timeStyle = .short
        } else {
            formatter.setLocalizedDateFormatFromTemplate("MMMd jj:mm")
        }
        return formatter.string(from: date)
    }
}
