import SwiftUI

/// The whole chat: home, thread, pre-chat form and rating.
struct HelpiumChatView: View {
    @ObservedObject var store: ChatStore
    let onClose: (() -> Void)?
    @Environment(\.colorScheme) private var systemScheme

    private var isDark: Bool {
        switch store.appearance.colorScheme {
        case .light: return false
        case .dark: return true
        case .system: return systemScheme == .dark
        }
    }

    private var palette: Palette {
        .resolve(store.appearance, workspacePrimary: store.settings?.primaryColor, dark: isDark)
    }

    var body: some View {
        NavigationView {
            content
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(palette.background.ignoresSafeArea())
                .navigationBarTitleDisplayMode(.inline)
                .headerStyle(palette)
                .toolbar {
                    ToolbarItem(placement: .navigationBarTrailing) {
                        if let onClose {
                            Button(action: onClose) { Image(systemName: "xmark").foregroundColor(palette.headerText) }
                                .accessibilityLabel(L.t("close"))
                        }
                    }
                }
        }
        .navigationViewStyle(.stack)
        .environment(\.palette, palette)
        .environment(\.colorScheme, isDark ? .dark : .light)
        .accentColor(palette.primary)
        .onAppear { store.isVisible = true }
        .onDisappear { store.isVisible = false }
        .task { await store.start() }
        .alert(store.notice ?? "", isPresented: Binding(get: { store.notice != nil }, set: { if !$0 { store.notice = nil } })) {
            Button("OK", role: .cancel) {}
        }
    }

    @ViewBuilder
    private var content: some View {
        switch store.screen {
        case .loading:
            ProgressView().tint(palette.textSecondary).headerTitle(L.t("title"), palette)
        case .failed:
            VStack(spacing: 12) {
                Text(L.t("loading_failed")).multilineTextAlignment(.center).foregroundColor(palette.textSecondary)
                Button(L.t("retry")) { Task { await store.reload() } }.foregroundColor(palette.primary)
            }
            .padding()
            .headerTitle(L.t("title"), palette)
        case .unavailable:
            Text(L.t("offline")).foregroundColor(palette.textSecondary).padding().headerTitle(L.t("title"), palette)
        case .home:
            HomeView(store: store)
        case .thread:
            ThreadView(store: store)
        }
    }
}

extension View {
    /// The top bar in the header colors. iOS 16+ paints its background; iOS 15 keeps the system bar.
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

    /// The screen title in the header text color.
    func headerTitle(_ title: String, _ palette: Palette) -> some View {
        toolbar {
            ToolbarItem(placement: .principal) {
                Text(title).font(.headline).foregroundColor(palette.headerText)
            }
        }
    }
}

struct HomeView: View {
    @ObservedObject var store: ChatStore
    @Environment(\.palette) private var palette

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                VStack(alignment: .leading, spacing: 6) {
                    Text(L.t("welcome_title")).font(.largeTitle.bold()).foregroundColor(palette.text)
                    Text(L.t("welcome_subtitle")).foregroundColor(palette.textSecondary)
                    if store.settings?.isOnline == false {
                        Text(store.settings?.offlineMessage ?? L.t("offline"))
                            .font(.footnote)
                            .foregroundColor(palette.textSecondary)
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
                                .background(Capsule().fill(palette.unreadBadge))
                                .foregroundColor(palette.unreadBadgeText)
                        }
                        Image(systemName: "paperplane.fill")
                    }
                    .padding()
                    .frame(maxWidth: .infinity)
                    .background(RoundedRectangle(cornerRadius: 14).fill(palette.primary))
                    .foregroundColor(palette.onPrimary)
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
                                    Image(systemName: "chevron.right").font(.footnote).foregroundColor(palette.textSecondary)
                                }
                                .padding()
                                .background(RoundedRectangle(cornerRadius: 12).fill(palette.surface))
                                .overlay(RoundedRectangle(cornerRadius: 12).stroke(palette.border))
                            }
                            .foregroundColor(palette.text)
                        }
                    }
                }
            }
            .padding()
        }
        .headerTitle(L.t("title"), palette)
    }
}
