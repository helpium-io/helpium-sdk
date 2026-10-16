import SwiftUI

struct MessageRow: View {
    let message: ChatMessage
    let isLatest: Bool
    let botName: String
    let botAvatar: String?
    let interactionsEnabled: Bool
    let onFeedback: (Bool) -> Void
    let onRetry: () -> Void
    let onSend: (String) -> Void

    @State private var showSources = false
    @Environment(\.palette) private var palette

    var body: some View {
        switch message.role {
        case .system:
            Text(message.content)
                .font(.footnote)
                .foregroundColor(palette.textSecondary)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 4)
        case .customer:
            HStack {
                Spacer(minLength: 48)
                VStack(alignment: .trailing, spacing: 4) {
                    images
                    if !message.content.isEmpty {
                        bubble(fill: palette.customerBubble, text: palette.customerBubbleText, link: palette.customerBubbleText)
                    }
                }
                .opacity(message.isPending ? 0.6 : 1)
            }
        case .bot, .agent:
            HStack(alignment: .bottom, spacing: 8) {
                Avatar(url: message.role == .bot ? botAvatar : message.senderAvatarUrl, fallback: senderName)
                VStack(alignment: .leading, spacing: 6) {
                    Text(senderName).font(.caption).foregroundColor(palette.textSecondary)
                    images
                    if !message.content.isEmpty {
                        bubble(fill: palette.agentBubble, text: palette.agentBubbleText, link: palette.link)
                    }
                    files
                    if !message.sources.isEmpty { sources }
                    interactive
                    if message.role == .bot && message.interactive != .retry && !message.content.isEmpty { feedback }
                }
                Spacer(minLength: 32)
            }
        }
    }

    private var senderName: String {
        message.role == .bot ? botName : (message.senderName ?? "")
    }

    private func bubble(fill: Color, text: Color, link: Color) -> some View {
        Text(Self.markdown(message.content))
            .foregroundColor(text)
            .tint(link)
            .padding(.horizontal, 14).padding(.vertical, 10)
            .background(RoundedRectangle(cornerRadius: 18).fill(fill))
            .textSelection(.enabled)
    }

    static func markdown(_ content: String) -> AttributedString {
        let options = AttributedString.MarkdownParsingOptions(interpretedSyntax: .inlineOnlyPreservingWhitespace)
        return (try? AttributedString(markdown: content, options: options)) ?? AttributedString(content)
    }

    @ViewBuilder
    private var images: some View {
        if let data = message.localImage, let image = UIImage(data: data) {
            Image(uiImage: image).resizable().scaledToFit().frame(maxWidth: 220).clipShape(RoundedRectangle(cornerRadius: 14))
        } else {
            ForEach(message.attachments.filter(\.isImage), id: \.self) { attachment in
                if let url = URL(string: attachment.url) {
                    Link(destination: url) {
                        AsyncImage(url: url) { image in image.resizable().scaledToFit() } placeholder: {
                            RoundedRectangle(cornerRadius: 14).fill(palette.surface).frame(width: 200, height: 150)
                        }
                        .frame(maxWidth: 220)
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var files: some View {
        ForEach(message.attachments.filter { !$0.isImage }, id: \.self) { attachment in
            if let url = URL(string: attachment.url) {
                Link(destination: url) {
                    Label(attachment.name ?? url.lastPathComponent, systemImage: "doc")
                        .font(.subheadline)
                        .padding(10)
                        .foregroundColor(palette.link)
                        .background(RoundedRectangle(cornerRadius: 12).stroke(palette.border))
                }
            }
        }
    }

    private var sources: some View {
        VStack(alignment: .leading, spacing: 4) {
            Button { showSources.toggle() } label: {
                Label(L.t("sources"), systemImage: showSources ? "chevron.down" : "chevron.right").font(.caption)
            }
            .foregroundColor(palette.link)
            if showSources {
                ForEach(message.sources, id: \.self) { source in
                    if let link = source.url.flatMap(URL.init(string:)) {
                        Link(source.title, destination: link).font(.caption).foregroundColor(palette.link)
                    } else {
                        Text(source.title).font(.caption).foregroundColor(palette.textSecondary)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var interactive: some View {
        switch message.interactive {
        case .retry:
            Button(L.t("retry"), action: onRetry).buttonStyle(PillButtonStyle(palette: palette, filled: false))
        case .confirm where isLatest && interactionsEnabled:
            HStack {
                Button(L.t("accept")) { onSend(L.t("accept")) }.buttonStyle(PillButtonStyle(palette: palette, filled: true))
                Button(L.t("cancel")) { onSend(L.t("cancel")) }.buttonStyle(PillButtonStyle(palette: palette, filled: false))
            }
        case .suggestions(let options) where isLatest && interactionsEnabled:
            VStack(alignment: .leading, spacing: 6) {
                ForEach(options, id: \.self) { option in
                    Button(option) { onSend(option) }.buttonStyle(PillButtonStyle(palette: palette, filled: false))
                }
            }
        default:
            EmptyView()
        }
    }

    private var feedback: some View {
        HStack(spacing: 14) {
            Button { onFeedback(true) } label: {
                Image(systemName: message.feedback == "up" ? "hand.thumbsup.fill" : "hand.thumbsup")
            }
            Button { onFeedback(false) } label: {
                Image(systemName: message.feedback == "down" ? "hand.thumbsdown.fill" : "hand.thumbsdown")
            }
        }
        .font(.caption)
        .foregroundColor(palette.textSecondary)
        .disabled(message.feedback != nil)
    }
}

/// A rounded button in the chat colors: filled with `primary`, or outlined.
struct PillButtonStyle: ButtonStyle {
    let palette: Palette
    let filled: Bool

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.subheadline.weight(.semibold))
            .foregroundColor(filled ? palette.onPrimary : palette.primary)
            .padding(.horizontal, 14).padding(.vertical, 8)
            .background(Capsule().fill(filled ? palette.primary : Color.clear))
            .overlay(Capsule().stroke(palette.primary, lineWidth: filled ? 0 : 1))
            .opacity(configuration.isPressed ? 0.7 : 1)
    }
}

struct PrechatView: View {
    let collectOptIn: Bool
    let palette: Palette
    let onSubmit: (String, String, String, Bool) -> Void

    @State private var firstName = ""
    @State private var lastName = ""
    @State private var email = ""
    @State private var optIn = false
    @Environment(\.dismiss) private var dismiss

    private var valid: Bool {
        !firstName.trimmingCharacters(in: .whitespaces).isEmpty
            && email.range(of: #"^[^@\s]+@[^@\s]+\.[^@\s]+$"#, options: .regularExpression) != nil
    }

    var body: some View {
        NavigationView {
            VStack(alignment: .leading, spacing: 14) {
                Text(L.t("prechat_subtitle")).font(.subheadline).foregroundColor(palette.textSecondary)
                field(L.t("first_name"), text: $firstName).textContentType(.givenName)
                field(L.t("last_name"), text: $lastName).textContentType(.familyName)
                field(L.t("email"), text: $email)
                    .textContentType(.emailAddress)
                    .keyboardType(.emailAddress)
                    .autocapitalization(.none)
                    .disableAutocorrection(true)
                if collectOptIn {
                    Toggle(L.t("marketing_opt_in"), isOn: $optIn).foregroundColor(palette.text).tint(palette.primary)
                }
                Button {
                    onSubmit(firstName.trimmingCharacters(in: .whitespaces), lastName.trimmingCharacters(in: .whitespaces), email.trimmingCharacters(in: .whitespaces), optIn)
                    dismiss()
                } label: {
                    Text(L.t("continue")).bold().frame(maxWidth: .infinity).padding(.vertical, 13)
                        .background(RoundedRectangle(cornerRadius: 12).fill(palette.primary))
                        .foregroundColor(palette.onPrimary)
                }
                .disabled(!valid)
                .opacity(valid ? 1 : 0.5)
                Spacer()
            }
            .padding()
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(palette.background.ignoresSafeArea())
            .navigationBarTitleDisplayMode(.inline)
            .headerStyle(palette)
            .headerTitle(L.t("prechat_title"), palette)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(L.t("cancel")) { dismiss() }.foregroundColor(palette.headerText)
                }
            }
        }
        .environment(\.colorScheme, palette.isDark ? .dark : .light)
        .accentColor(palette.primary)
    }

    private func field(_ placeholder: String, text: Binding<String>) -> some View {
        ZStack(alignment: .leading) {
            if text.wrappedValue.isEmpty {
                Text(placeholder).foregroundColor(palette.textSecondary).allowsHitTesting(false)
            }
            TextField("", text: text).foregroundColor(palette.inputText)
        }
        .padding(.horizontal, 14).padding(.vertical, 12)
        .background(RoundedRectangle(cornerRadius: 12).fill(palette.inputBackground))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(palette.border))
        .accessibilityLabel(placeholder)
    }
}

struct RatingView: View {
    let onRate: (Int, String?) -> Void
    let onSkip: () -> Void

    @State private var selected: Int?
    @State private var comment = ""
    @Environment(\.palette) private var palette

    private let faces = ["😞", "😐", "🙂", "😀", "🤩"]

    var body: some View {
        VStack(spacing: 12) {
            Text(L.t("rate_title")).font(.headline).foregroundColor(palette.text)
            HStack(spacing: 14) {
                ForEach(1...5, id: \.self) { rating in
                    Button {
                        if rating >= 3 {
                            onRate(rating, nil)
                        } else {
                            selected = rating
                        }
                    } label: {
                        VStack(spacing: 2) {
                            Text(faces[rating - 1]).font(.system(size: 30))
                            Text(L.t("rating_\(rating)")).font(.caption2).foregroundColor(palette.textSecondary)
                        }
                        .opacity(selected == nil || selected == rating ? 1 : 0.4)
                    }
                }
            }
            if let selected {
                ZStack(alignment: .leading) {
                    if comment.isEmpty {
                        Text(L.t("rate_comment")).foregroundColor(palette.textSecondary).allowsHitTesting(false)
                    }
                    TextField("", text: $comment).foregroundColor(palette.inputText)
                }
                .padding(.horizontal, 12).padding(.vertical, 10)
                .background(RoundedRectangle(cornerRadius: 10).fill(palette.inputBackground))
                Button(L.t("rate_send")) { onRate(selected, comment) }.buttonStyle(PillButtonStyle(palette: palette, filled: true))
            }
            Button(L.t("rate_skip"), action: onSkip).font(.footnote).foregroundColor(palette.textSecondary)
        }
        .padding()
        .background(palette.background)
    }
}
