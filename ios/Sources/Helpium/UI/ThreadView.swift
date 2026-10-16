import SwiftUI
import UIKit

struct ThreadView: View {
    @ObservedObject var store: ChatStore
    let onClose: (() -> Void)?
    @Environment(\.palette) private var palette

    @State private var draft = ""
    @State private var confirmClose = false
    @State private var picker: UIImagePickerController.SourceType?
    @State private var prechatPending: String?

    var body: some View {
        VStack(spacing: 0) {
            header
            messageList
            footer
        }
        .confirmationDialog(L.t("close_confirm_title"), isPresented: $confirmClose, titleVisibility: .visible) {
            Button(L.t("end"), role: .destructive) { store.close() }
            Button(L.t("cancel"), role: .cancel) {}
        } message: {
            Text(L.t("close_confirm_message"))
        }
        .sheet(item: Binding(get: { picker.map(PickerSource.init) }, set: { picker = $0?.type })) { source in
            ImagePicker(sourceType: source.type) { image in
                guard let data = ImageEncoder.jpeg(image) else { return }
                store.send(text: "", image: (data, "image/jpeg"))
            }
            .ignoresSafeArea()
        }
        .sheet(isPresented: Binding(get: { prechatPending != nil }, set: { if !$0 { prechatPending = nil } })) {
            PrechatView(collectOptIn: store.settings?.collectMarketingOptIn ?? false, palette: palette) { first, last, email, optIn in
                store.submitPrechat(firstName: first, lastName: last, email: email, marketingOptIn: optIn)
                if let text = prechatPending { store.send(text: text) }
                prechatPending = nil
            }
        }
    }

    // MARK: - Header

    private var headerName: String {
        store.agent?.name ?? store.settings?.botName ?? L.t("title")
    }

    /// Like the web widget: an empty chat greets; a conversation says who is
    /// answering and how fast.
    private var headerLines: (top: String, main: String, mainFirst: Bool) {
        if !store.hasConversation {
            return (store.settings?.botName ?? "", L.t("greeting"), false)
        }
        if store.agent == nil && store.handoffRequested && !store.isClosed {
            return (headerName, L.t("waiting_for_agent"), true)
        }
        return (headerName, L.t("reply_time"), true)
    }

    private var header: some View {
        HStack(spacing: 10) {
            Avatar(url: store.agent?.avatarUrl ?? store.settings?.botAvatarUrl, fallback: headerName, size: 36)
                .overlay(alignment: .bottomTrailing) {
                    if store.settings?.isOnline != false {
                        Circle().fill(Color(hex: "#22c55e")).frame(width: 10, height: 10)
                            .overlay(Circle().stroke(palette.headerBackground, lineWidth: 2))
                    }
                }
                .padding(.leading, 6)

            VStack(alignment: .leading, spacing: 1) {
                let lines = headerLines
                if lines.mainFirst {
                    Text(lines.top).font(.subheadline.weight(.semibold)).foregroundColor(palette.headerText).lineLimit(1)
                    Text(lines.main).font(.caption).foregroundColor(palette.headerText.opacity(0.65)).lineLimit(1)
                } else {
                    if !lines.top.isEmpty {
                        Text(lines.top).font(.caption).foregroundColor(palette.headerText.opacity(0.65)).lineLimit(1)
                    }
                    Text(lines.main).font(.subheadline.weight(.semibold)).foregroundColor(palette.headerText).lineLimit(2)
                }
            }

            Spacer(minLength: 4)

            if store.hasConversation && !store.isClosed {
                Menu {
                    Button(role: .destructive) { confirmClose = true } label: { Label(L.t("close_conversation"), systemImage: "checkmark.circle") }
                } label: {
                    Image(systemName: "ellipsis")
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundColor(palette.headerText)
                        .frame(width: 34, height: 34)
                }
                .accessibilityLabel(L.t("more"))
            }
            CloseButton(onClose: onClose, color: palette.headerText)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .background(palette.headerBackground.ignoresSafeArea(edges: .top))
        .overlay(palette.border.frame(height: 1), alignment: .bottom)
    }

    // MARK: - Messages

    private var lastMessageId: Int? { store.messages.last?.id }

    private func sameSender(_ a: ChatMessage?, _ b: ChatMessage?) -> Bool {
        guard let a, let b, a.role == b.role, a.role != .system else { return false }
        return a.role != .agent || a.senderName == b.senderName
    }

    private var messageList: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0) {
                    if store.messages.isEmpty {
                        WelcomeView(store: store)
                    }
                    ForEach(Array(store.messages.enumerated()), id: \.element.id) { index, message in
                        let previous = index > 0 ? store.messages[index - 1] : nil
                        let next = index < store.messages.count - 1 ? store.messages[index + 1] : nil
                        MessageRow(
                            message: message,
                            isLatest: message.id == lastMessageId,
                            isFirstInGroup: !sameSender(previous, message),
                            isLastInGroup: !sameSender(message, next),
                            botName: store.settings?.botName ?? "",
                            botAvatar: store.settings?.botAvatarUrl,
                            interactionsEnabled: !store.isBotTyping && !store.isClosed,
                            onFeedback: { up in store.feedback(messageId: message.id, up: up) },
                            onRetry: { store.retry(messageId: message.id) },
                            onSend: { text in store.send(text: text) }
                        )
                        .padding(.top, sameSender(previous, message) ? 3 : 14)
                        .id(message.id)
                    }

                    if store.showHandoffButton {
                        Button { store.requestHuman() } label: {
                            Label(L.t("talk_to_human"), systemImage: "person.fill")
                        }
                        .buttonStyle(PillButtonStyle(palette: palette, filled: false))
                        .frame(maxWidth: .infinity)
                        .padding(.top, 14)
                        .id("handoff")
                    }

                    if store.isBotTyping || store.isAgentTyping {
                        TypingRow(
                            status: store.isBotTyping ? store.botTypingStatus : nil,
                            avatar: store.isBotTyping ? store.settings?.botAvatarUrl : store.agent?.avatarUrl,
                            name: store.isBotTyping ? (store.settings?.botName ?? "") : (store.agent?.name ?? "")
                        )
                        .padding(.top, 14)
                        .id("typing")
                    }
                }
                .padding(.horizontal, 14)
                .padding(.bottom, 14)
            }
            .onChange(of: store.messages.count) { _ in scrollToBottom(proxy) }
            .onChange(of: store.isBotTyping) { _ in scrollToBottom(proxy) }
            .onChange(of: store.showHandoffButton) { _ in scrollToBottom(proxy) }
            .onAppear { scrollToBottom(proxy, animated: false) }
        }
    }

    private func scrollToBottom(_ proxy: ScrollViewProxy, animated: Bool = true) {
        // The last thing in the list: the typing dots, the handoff button, or the last message.
        let target: AnyHashable?
        if store.isBotTyping || store.isAgentTyping {
            target = AnyHashable("typing")
        } else if store.showHandoffButton {
            target = AnyHashable("handoff")
        } else {
            target = lastMessageId.map(AnyHashable.init)
        }
        guard let target else { return }
        if animated {
            withAnimation(.easeOut(duration: 0.25)) { proxy.scrollTo(target, anchor: .bottom) }
        } else {
            proxy.scrollTo(target, anchor: .bottom)
        }
    }

    // MARK: - Footer

    @ViewBuilder
    private var footer: some View {
        if store.showRating {
            RatingView { rating, comment in store.rate(rating, comment: comment) } onSkip: { store.skipRating() }
                .overlay(palette.border.frame(height: 1), alignment: .top)
        } else if store.isClosed {
            VStack(spacing: 12) {
                Text(closedText).font(.footnote).foregroundColor(palette.textSecondary)
                Button(L.t("start_new")) { store.startNewConversation() }.buttonStyle(PillButtonStyle(palette: palette, filled: true))
            }
            .frame(maxWidth: .infinity)
            .padding(16)
            .overlay(palette.border.frame(height: 1), alignment: .top)
        } else {
            composer
        }
    }

    private var closedText: String {
        switch store.closedReason {
        case "customer": return L.t("closed_customer")
        case "inactivity": return L.t("closed_inactivity")
        case "agent", "rule", "unrelated", "automated": return L.t("closed_team")
        default: return L.t("closed")
        }
    }

    private var canSend: Bool {
        !draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !store.isBotTyping
    }

    private var composer: some View {
        HStack(alignment: .bottom, spacing: 8) {
            HStack(alignment: .bottom, spacing: 4) {
                ZStack(alignment: .leading) {
                    if draft.isEmpty {
                        Text(L.t("placeholder")).foregroundColor(palette.textSecondary).allowsHitTesting(false)
                    }
                    TextField("", text: $draft)
                        .textFieldStyle(.plain)
                        .foregroundColor(palette.inputText)
                        .onChange(of: draft) { value in if !value.isEmpty { store.customerTyping() } }
                        .onSubmit(send)
                        .submitLabel(.send)
                }
                .padding(.leading, 14)
                .padding(.vertical, 11)
                .accessibilityLabel(L.t("placeholder"))

                Menu {
                    Button { picker = .photoLibrary } label: { Label(L.t("photo_library"), systemImage: "photo") }
                    if UIImagePickerController.isSourceTypeAvailable(.camera) {
                        Button { picker = .camera } label: { Label(L.t("camera"), systemImage: "camera") }
                    }
                } label: {
                    Image(systemName: "paperclip")
                        .font(.system(size: 17, weight: .medium))
                        .foregroundColor(palette.textSecondary)
                        .frame(width: 38, height: 42)
                }
                .disabled(store.needsPrechat)
                .accessibilityLabel(L.t("attach_image"))
            }
            .background(RoundedRectangle(cornerRadius: 22, style: .continuous).fill(palette.inputBackground))
            .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous).stroke(palette.border, lineWidth: 1))

            Button(action: send) {
                Image(systemName: "arrow.up")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundColor(canSend ? palette.onPrimary : palette.textSecondary)
                    .frame(width: 44, height: 44)
                    .background(Circle().fill(canSend ? palette.primary : palette.surface))
            }
            .disabled(!canSend)
            .animation(.easeOut(duration: 0.15), value: canSend)
            .accessibilityLabel(L.t("send"))
        }
        .padding(.horizontal, 12)
        .padding(.top, 10)
        .padding(.bottom, 10)
        .background(palette.background)
        .overlay(palette.border.frame(height: 1), alignment: .top)
    }

    private func send() {
        let text = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, !store.isBotTyping else { return }
        draft = ""
        if store.needsPrechat {
            prechatPending = text
        } else {
            store.send(text: text)
        }
    }
}

/// What an empty chat shows, like the web widget: a greeting, how fast the
/// team answers, and the instant answers as one-tap questions.
struct WelcomeView: View {
    @ObservedObject var store: ChatStore
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 0) {
            Text(L.t("welcome_title"))
                .font(.system(size: 22, weight: .bold))
                .foregroundColor(palette.text)
                .multilineTextAlignment(.center)
                .padding(.top, 40)
            Text(store.settings?.aiAvailable == false ? L.t("welcome_subtitle_team") : L.t("welcome_subtitle"))
                .font(.subheadline)
                .foregroundColor(palette.textSecondary)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 8)
                .padding(.horizontal, 24)

            if let answers = store.settings?.instantAnswers, !answers.isEmpty {
                VStack(spacing: 8) {
                    Text(L.t("instant_answers").uppercased())
                        .font(.caption2.weight(.semibold))
                        .foregroundColor(palette.textSecondary)
                        .padding(.bottom, 2)
                    ForEach(answers) { answer in
                        Button(answer.label) {
                            store.sendInstantAnswer(answer)
                        }
                        .buttonStyle(PillButtonStyle(palette: palette, filled: false))
                    }
                }
                .padding(.top, 28)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.bottom, 12)
    }
}

private struct PickerSource: Identifiable {
    let type: UIImagePickerController.SourceType
    var id: Int { type.rawValue }
}

/// Three dots bouncing in an agent bubble, or a status line while an AI action runs.
struct TypingRow: View {
    let status: String?
    let avatar: String?
    let name: String
    @Environment(\.palette) private var palette
    @State private var animate = false

    var body: some View {
        HStack(alignment: .bottom, spacing: 8) {
            Avatar(url: avatar, fallback: name, size: 28)
            HStack(spacing: 5) {
                if let status {
                    ProgressView().scaleEffect(0.7).tint(palette.agentBubbleText.opacity(0.6))
                    Text(status).font(.footnote).foregroundColor(palette.agentBubbleText.opacity(0.75))
                } else {
                    ForEach(0..<3) { index in
                        Circle()
                            .fill(palette.agentBubbleText.opacity(0.45))
                            .frame(width: 7, height: 7)
                            .offset(y: animate ? -3 : 3)
                            .animation(.easeInOut(duration: 0.45).repeatForever().delay(Double(index) * 0.15), value: animate)
                    }
                }
            }
            .padding(.horizontal, 16).padding(.vertical, 14)
            .background(BubbleShape(isCustomer: false, isLast: true).fill(palette.agentBubble))
            .accessibilityLabel(L.t("typing"))
            Spacer(minLength: 40)
        }
        .onAppear { animate = true }
    }
}

enum ImageEncoder {
    /// A JPEG under the 5 MB the API accepts, at most 2048 px on its long side.
    static func jpeg(_ image: UIImage) -> Data? {
        let maxSide: CGFloat = 2048
        let scale = min(1, maxSide / max(image.size.width, image.size.height))
        let size = CGSize(width: image.size.width * scale, height: image.size.height * scale)
        let resized = UIGraphicsImageRenderer(size: size).image { _ in image.draw(in: CGRect(origin: .zero, size: size)) }
        var quality: CGFloat = 0.8
        var data = resized.jpegData(compressionQuality: quality)
        while let current = data, current.count > 4_500_000, quality > 0.2 {
            quality -= 0.15
            data = resized.jpegData(compressionQuality: quality)
        }
        return data
    }
}

struct ImagePicker: UIViewControllerRepresentable {
    let sourceType: UIImagePickerController.SourceType
    let onPick: (UIImage) -> Void
    @Environment(\.dismiss) private var dismiss

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let controller = UIImagePickerController()
        controller.sourceType = sourceType
        controller.delegate = context.coordinator
        return controller
    }

    func updateUIViewController(_ controller: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let parent: ImagePicker

        init(_ parent: ImagePicker) { self.parent = parent }

        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            if let image = info[.originalImage] as? UIImage { parent.onPick(image) }
            parent.dismiss()
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            parent.dismiss()
        }
    }
}
