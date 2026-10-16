import SwiftUI
import UIKit

struct ThreadView: View {
    @ObservedObject var store: ChatStore
    let accent: Color

    @State private var draft = ""
    @State private var confirmClose = false
    @State private var picker: UIImagePickerController.SourceType?
    @State private var prechatPending: String?

    var body: some View {
        VStack(spacing: 0) {
            messageList
            Divider()
            footer
        }
        .navigationTitle(title)
        .toolbar {
            ToolbarItem(placement: .navigationBarLeading) {
                Button { store.goHome() } label: { Image(systemName: "chevron.left") }
            }
            ToolbarItem(placement: .principal) {
                if store.hasConversation && !store.isClosed {
                    Menu {
                        Button(role: .destructive) { confirmClose = true } label: { Label(L.t("close_conversation"), systemImage: "checkmark.circle") }
                    } label: {
                        HStack(spacing: 4) {
                            Text(title).font(.headline).foregroundColor(.primary)
                            Image(systemName: "chevron.down").font(.caption2).foregroundColor(.secondary)
                        }
                    }
                } else {
                    Text(title).font(.headline)
                }
            }
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
            PrechatView(collectOptIn: store.settings?.collectMarketingOptIn ?? false, accent: accent) { first, last, email, optIn in
                store.submitPrechat(firstName: first, lastName: last, email: email, marketingOptIn: optIn)
                if let text = prechatPending { store.send(text: text) }
                prechatPending = nil
            }
        }
    }

    private var title: String {
        if let agent = store.agent { return L.t("chatting_with", agent.name) }
        if store.handoffRequested && !store.isClosed { return L.t("waiting_for_agent") }
        return store.settings?.botName ?? L.t("title")
    }

    private var lastMessageId: Int? { store.messages.last?.id }

    private var messageList: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 10) {
                    ForEach(store.messages) { message in
                        MessageRow(
                            message: message,
                            isLatest: message.id == lastMessageId,
                            botName: store.settings?.botName ?? "",
                            botAvatar: store.settings?.botAvatarUrl,
                            accent: accent,
                            accentText: Color.contrasting(store.settings?.primaryColor ?? "#6366f1"),
                            interactionsEnabled: !store.isBotTyping && !store.isClosed,
                            onFeedback: { up in store.feedback(messageId: message.id, up: up) },
                            onRetry: { store.retry(messageId: message.id) },
                            onSend: { text in store.send(text: text) }
                        )
                        .id(message.id)
                    }

                    if store.showHandoffButton {
                        Button(L.t("talk_to_human")) { store.requestHuman() }
                            .buttonStyle(.bordered)
                            .padding(.leading, 36)
                    }

                    if store.isBotTyping || store.isAgentTyping {
                        TypingRow(status: store.isBotTyping ? store.botTypingStatus : nil, avatar: store.isBotTyping ? store.settings?.botAvatarUrl : store.agent?.avatarUrl, name: store.isBotTyping ? (store.settings?.botName ?? "") : (store.agent?.name ?? ""))
                            .id("typing")
                    }
                }
                .padding()
            }
            .onChange(of: store.messages.count) { _ in scrollToBottom(proxy) }
            .onChange(of: store.isBotTyping) { _ in scrollToBottom(proxy) }
            .onAppear { scrollToBottom(proxy, animated: false) }
        }
    }

    private func scrollToBottom(_ proxy: ScrollViewProxy, animated: Bool = true) {
        let target: AnyHashable? = (store.isBotTyping || store.isAgentTyping) ? AnyHashable("typing") : lastMessageId.map(AnyHashable.init)
        guard let target else { return }
        if animated {
            withAnimation { proxy.scrollTo(target, anchor: .bottom) }
        } else {
            proxy.scrollTo(target, anchor: .bottom)
        }
    }

    @ViewBuilder
    private var footer: some View {
        if store.showRating {
            RatingView(accent: accent) { rating, comment in store.rate(rating, comment: comment) } onSkip: { store.skipRating() }
        } else if store.isClosed {
            VStack(spacing: 10) {
                Text(closedText).font(.footnote).foregroundColor(.secondary)
                Button(L.t("start_new")) { store.startNewConversation() }.buttonStyle(.borderedProminent)
            }
            .padding()
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

    private var composer: some View {
        HStack(alignment: .bottom, spacing: 8) {
            Menu {
                Button { picker = .photoLibrary } label: { Label(L.t("photo_library"), systemImage: "photo") }
                if UIImagePickerController.isSourceTypeAvailable(.camera) {
                    Button { picker = .camera } label: { Label(L.t("camera"), systemImage: "camera") }
                }
            } label: {
                Image(systemName: "paperclip").font(.system(size: 20)).padding(8)
            }
            .disabled(store.needsPrechat)

            TextField(L.t("placeholder"), text: $draft)
                .textFieldStyle(.plain)
                .padding(.horizontal, 12).padding(.vertical, 9)
                .background(RoundedRectangle(cornerRadius: 18).fill(Color(.secondarySystemBackground)))
                .onChange(of: draft) { value in if !value.isEmpty { store.customerTyping() } }
                .onSubmit(send)
                .submitLabel(.send)

            Button(action: send) {
                Image(systemName: "arrow.up.circle.fill").font(.system(size: 30))
            }
            .disabled(draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || store.isBotTyping)
        }
        .padding(.horizontal, 10).padding(.vertical, 8)
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

private struct PickerSource: Identifiable {
    let type: UIImagePickerController.SourceType
    var id: Int { type.rawValue }
}

struct TypingRow: View {
    let status: String?
    let avatar: String?
    let name: String
    @State private var phase = 0.0

    var body: some View {
        HStack(alignment: .bottom, spacing: 8) {
            Avatar(url: avatar, fallback: name)
            HStack(spacing: 6) {
                if let status {
                    ProgressView().scaleEffect(0.7)
                    Text(status).font(.footnote).foregroundColor(.secondary)
                } else {
                    ForEach(0..<3) { index in
                        Circle().fill(Color.secondary).frame(width: 7, height: 7)
                            .opacity(0.3 + 0.7 * abs(sin(phase + Double(index) * 0.6)))
                    }
                }
            }
            .padding(.horizontal, 14).padding(.vertical, 12)
            .background(RoundedRectangle(cornerRadius: 18).fill(Color(.secondarySystemBackground)))
            .accessibilityLabel(L.t("typing"))
        }
        .onAppear { withAnimation(.linear(duration: 1.2).repeatForever(autoreverses: false)) { phase = .pi } }
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
