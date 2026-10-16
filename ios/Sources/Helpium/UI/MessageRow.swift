import SwiftUI

struct MessageRow: View {
    let message: ChatMessage
    let isLatest: Bool
    let botName: String
    let botAvatar: String?
    let accent: Color
    let accentText: Color
    let interactionsEnabled: Bool
    let onFeedback: (Bool) -> Void
    let onRetry: () -> Void
    let onSend: (String) -> Void

    @State private var showSources = false

    var body: some View {
        switch message.role {
        case .system:
            Text(message.content)
                .font(.footnote)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 4)
        case .customer:
            HStack {
                Spacer(minLength: 48)
                VStack(alignment: .trailing, spacing: 4) {
                    images
                    if !message.content.isEmpty {
                        bubble(fill: accent, text: accentText)
                    }
                }
                .opacity(message.isPending ? 0.6 : 1)
            }
        case .bot, .agent:
            HStack(alignment: .bottom, spacing: 8) {
                Avatar(url: message.role == .bot ? botAvatar : message.senderAvatarUrl, fallback: senderName)
                VStack(alignment: .leading, spacing: 6) {
                    Text(senderName).font(.caption).foregroundColor(.secondary)
                    images
                    if !message.content.isEmpty {
                        bubble(fill: Color(.secondarySystemBackground), text: .primary)
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

    private func bubble(fill: Color, text: Color) -> some View {
        Text(Self.markdown(message.content))
            .foregroundColor(text)
            .tint(text == .primary ? accent : text)
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
                            RoundedRectangle(cornerRadius: 14).fill(Color(.secondarySystemBackground)).frame(width: 200, height: 150)
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
                        .background(RoundedRectangle(cornerRadius: 12).stroke(Color(.separator)))
                }
            }
        }
    }

    private var sources: some View {
        VStack(alignment: .leading, spacing: 4) {
            Button { showSources.toggle() } label: {
                Label(L.t("sources"), systemImage: showSources ? "chevron.down" : "chevron.right").font(.caption)
            }
            if showSources {
                ForEach(message.sources, id: \.self) { source in
                    if let link = source.url.flatMap(URL.init(string:)) {
                        Link(source.title, destination: link).font(.caption)
                    } else {
                        Text(source.title).font(.caption)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var interactive: some View {
        switch message.interactive {
        case .retry:
            Button(L.t("retry"), action: onRetry).buttonStyle(.bordered)
        case .confirm where isLatest && interactionsEnabled:
            HStack {
                Button(L.t("accept")) { onSend(L.t("accept")) }.buttonStyle(.borderedProminent)
                Button(L.t("cancel")) { onSend(L.t("cancel")) }.buttonStyle(.bordered)
            }
        case .suggestions(let options) where isLatest && interactionsEnabled:
            VStack(alignment: .leading, spacing: 6) {
                ForEach(options, id: \.self) { option in
                    Button(option) { onSend(option) }.buttonStyle(.bordered)
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
        .foregroundColor(.secondary)
        .disabled(message.feedback != nil)
    }
}

struct PrechatView: View {
    let collectOptIn: Bool
    let accent: Color
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
            Form {
                Section(footer: Text(L.t("prechat_subtitle"))) {
                    TextField(L.t("first_name"), text: $firstName).textContentType(.givenName)
                    TextField(L.t("last_name"), text: $lastName).textContentType(.familyName)
                    TextField(L.t("email"), text: $email)
                        .textContentType(.emailAddress)
                        .keyboardType(.emailAddress)
                        .autocapitalization(.none)
                        .disableAutocorrection(true)
                }
                if collectOptIn {
                    Toggle(L.t("marketing_opt_in"), isOn: $optIn)
                }
                Button(L.t("continue")) {
                    onSubmit(firstName.trimmingCharacters(in: .whitespaces), lastName.trimmingCharacters(in: .whitespaces), email.trimmingCharacters(in: .whitespaces), optIn)
                    dismiss()
                }
                .disabled(!valid)
            }
            .navigationTitle(L.t("prechat_title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(L.t("cancel")) { dismiss() } }
            }
        }
        .accentColor(accent)
    }
}

struct RatingView: View {
    let accent: Color
    let onRate: (Int, String?) -> Void
    let onSkip: () -> Void

    @State private var selected: Int?
    @State private var comment = ""

    private let faces = ["😞", "😐", "🙂", "😀", "🤩"]

    var body: some View {
        VStack(spacing: 12) {
            Text(L.t("rate_title")).font(.headline)
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
                            Text(L.t("rating_\(rating)")).font(.caption2).foregroundColor(.secondary)
                        }
                        .opacity(selected == nil || selected == rating ? 1 : 0.4)
                    }
                }
            }
            if let selected {
                TextField(L.t("rate_comment"), text: $comment)
                    .textFieldStyle(.roundedBorder)
                Button(L.t("rate_send")) { onRate(selected, comment) }.buttonStyle(.borderedProminent)
            }
            Button(L.t("rate_skip"), action: onSkip).font(.footnote)
        }
        .padding()
        .tint(accent)
    }
}
