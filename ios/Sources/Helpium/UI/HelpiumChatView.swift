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
                .navigationBarHidden(true)
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
            VStack(spacing: 0) {
                PlainHeader(title: L.t("title"), onClose: onClose)
                Spacer()
                ProgressView().tint(palette.textSecondary)
                Spacer()
            }
        case .failed:
            VStack(spacing: 0) {
                PlainHeader(title: L.t("title"), onClose: onClose)
                Spacer()
                VStack(spacing: 14) {
                    Image(systemName: "wifi.exclamationmark").font(.system(size: 30)).foregroundColor(palette.textSecondary)
                    Text(L.t("loading_failed")).multilineTextAlignment(.center).foregroundColor(palette.textSecondary)
                    Button(L.t("retry")) { Task { await store.reload() } }.buttonStyle(PillButtonStyle(palette: palette, filled: true))
                }
                .padding(32)
                Spacer()
            }
        case .unavailable:
            VStack(spacing: 0) {
                PlainHeader(title: L.t("title"), onClose: onClose)
                Spacer()
                Text(L.t("offline")).multilineTextAlignment(.center).foregroundColor(palette.textSecondary).padding(32)
                Spacer()
            }
        case .thread:
            ThreadView(store: store, onClose: onClose)
        }
    }
}

/// A simple top bar for the loading and error states.
struct PlainHeader: View {
    let title: String
    let onClose: (() -> Void)?
    @Environment(\.palette) private var palette

    var body: some View {
        HStack {
            Text(title).font(.headline).foregroundColor(palette.headerText)
            Spacer()
            CloseButton(onClose: onClose, color: palette.headerText)
        }
        .padding(.horizontal, 16)
        .frame(height: 52)
        .background(palette.headerBackground.ignoresSafeArea(edges: .top))
    }
}

struct CloseButton: View {
    let onClose: (() -> Void)?
    let color: Color

    var body: some View {
        if let onClose {
            Button(action: onClose) {
                Image(systemName: "xmark")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(color)
                    .frame(width: 34, height: 34)
                    .background(Circle().fill(color.opacity(0.12)))
            }
            .accessibilityLabel(L.t("close"))
        }
    }
}
