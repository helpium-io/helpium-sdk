import SwiftUI

/// The whole chat: home, thread, pre-chat form and rating.
struct HelpiumChatView: View {
    @ObservedObject var store: ChatStore
    let onClose: (() -> Void)?

    var body: some View {
        NavigationView {
            content
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .navigationBarTrailing) {
                        if let onClose {
                            Button(action: onClose) { Image(systemName: "xmark") }
                                .accessibilityLabel(L.t("close"))
                        }
                    }
                }
        }
        .navigationViewStyle(.stack)
        .accentColor(accent)
        .onAppear { store.isVisible = true }
        .onDisappear { store.isVisible = false }
        .task { await store.start() }
        .alert(store.notice ?? "", isPresented: Binding(get: { store.notice != nil }, set: { if !$0 { store.notice = nil } })) {
            Button("OK", role: .cancel) {}
        }
    }

    private var accent: Color { Color(hex: store.settings?.primaryColor ?? "#6366f1") }

    @ViewBuilder
    private var content: some View {
        switch store.screen {
        case .loading:
            ProgressView().navigationTitle(L.t("title"))
        case .failed:
            VStack(spacing: 12) {
                Text(L.t("loading_failed")).multilineTextAlignment(.center).foregroundColor(.secondary)
                Button(L.t("retry")) { Task { await store.reload() } }
            }
            .padding()
            .navigationTitle(L.t("title"))
        case .unavailable:
            Text(L.t("offline")).foregroundColor(.secondary).padding().navigationTitle(L.t("title"))
        case .home:
            HomeView(store: store, accent: accent)
        case .thread:
            ThreadView(store: store, accent: accent)
        }
    }
}

struct HomeView: View {
    @ObservedObject var store: ChatStore
    let accent: Color

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                VStack(alignment: .leading, spacing: 6) {
                    Text(L.t("welcome_title")).font(.largeTitle.bold())
                    Text(L.t("welcome_subtitle")).foregroundColor(.secondary)
                    if store.settings?.isOnline == false {
                        Text(store.settings?.offlineMessage ?? L.t("offline"))
                            .font(.footnote)
                            .foregroundColor(.secondary)
                            .padding(.top, 4)
                    }
                }

                Button {
                    store.hasConversation ? store.openThread() : store.startNewConversation()
                } label: {
                    HStack {
                        Text(store.hasConversation ? L.t("continue_conversation") : L.t("new_conversation")).bold()
                        Spacer()
                        if store.unreadCount > 0 {
                            Text("\(store.unreadCount)")
                                .font(.caption.bold())
                                .padding(.horizontal, 7).padding(.vertical, 2)
                                .background(Capsule().fill(Color.red))
                                .foregroundColor(.white)
                        }
                        Image(systemName: "paperplane.fill")
                    }
                    .padding()
                    .frame(maxWidth: .infinity)
                    .background(RoundedRectangle(cornerRadius: 14).fill(accent))
                    .foregroundColor(Color.contrasting(store.settings?.primaryColor ?? "#6366f1"))
                }

                if !store.hasConversation, let answers = store.settings?.instantAnswers, !answers.isEmpty {
                    VStack(spacing: 8) {
                        ForEach(answers) { answer in
                            Button {
                                if answer.isShortcut && store.needsPrechat {
                                    store.startNewConversation()
                                } else {
                                    store.sendInstantAnswer(answer)
                                }
                            } label: {
                                HStack {
                                    Text(answer.label).multilineTextAlignment(.leading)
                                    Spacer()
                                    Image(systemName: "chevron.right").font(.footnote)
                                }
                                .padding()
                                .background(RoundedRectangle(cornerRadius: 12).stroke(Color(.separator)))
                            }
                            .foregroundColor(.primary)
                        }
                    }
                }
            }
            .padding()
        }
        .navigationTitle(L.t("title"))
    }
}
