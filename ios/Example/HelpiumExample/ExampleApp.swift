@_spi(Demo) import Helpium
import SwiftUI

/// Try the Helpium iOS SDK: paste a widget key, open the chat, switch colors.
///
/// The key and server can also come from the launch environment:
/// `SIMCTL_CHILD_HELPIUM_WIDGET_KEY=wk_... SIMCTL_CHILD_HELPIUM_BASE_URL=https://helpium.test xcrun simctl launch booted io.helpium.example`
@main
struct ExampleApp: App {
    var body: some Scene {
        WindowGroup { ContentView() }
    }
}

struct ContentView: View {
    @AppStorage("widgetKey") private var widgetKey = ""
    @AppStorage("baseUrl") private var baseUrl = "https://helpium.io"
    @State private var email = ""
    @State private var theme = 0
    @State private var unread = 0
    @State private var configured = false
    @State private var listener: HelpiumListener?

    var body: some View {
        NavigationView {
            Form {
                Section("Workspace") {
                    TextField("Widget key (wk_...)", text: $widgetKey).autocapitalization(.none).disableAutocorrection(true)
                    TextField("Server", text: $baseUrl).autocapitalization(.none).disableAutocorrection(true).keyboardType(.URL)
                    Button(configured ? "Reconnect" : "Connect") { configure() }.disabled(widgetKey.isEmpty)
                }

                Section("Chat") {
                    Button {
                        Helpium.present()
                    } label: {
                        HStack {
                            Label("Chat with support", systemImage: "bubble.left.and.bubble.right.fill")
                            Spacer()
                            if unread > 0 {
                                Text("\(unread)").font(.caption.bold()).foregroundColor(.white)
                                    .padding(.horizontal, 7).padding(.vertical, 2).background(Capsule().fill(.red))
                            }
                        }
                    }
                    .disabled(!configured)

                    NavigationLink("Embedded chat view") { Helpium.chatView().navigationBarHidden(true) }
                        .disabled(!configured)

                    Picker("Colors", selection: $theme) {
                        ForEach(Themes.names.indices, id: \.self) { Text(Themes.names[$0]).tag($0) }
                    }
                    .onChange(of: theme) { Helpium.setAppearance(Themes.appearances[$0]) }
                }

                Section("Customer") {
                    TextField("Email (optional)", text: $email).keyboardType(.emailAddress).autocapitalization(.none)
                    Button("Identify (unverified)") { Helpium.identify(email: email) }.disabled(email.isEmpty || !configured)
                    Button("Log out", role: .destructive) { Helpium.logout() }.disabled(!configured)
                }
            }
            .navigationTitle("Helpium SDK")
        }
        .onAppear {
            let environment = ProcessInfo.processInfo.environment
            if let key = environment["HELPIUM_WIDGET_KEY"], !key.isEmpty { widgetKey = key }
            if let url = environment["HELPIUM_BASE_URL"], !url.isEmpty { baseUrl = url }
            if !widgetKey.isEmpty && !configured { configure() }
            // HELPIUM_OPEN_CHAT=1 opens the chat at launch (handy for screenshots).
            if configured && environment["HELPIUM_OPEN_CHAT"] == "1" {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { Helpium.present() }
            }
            // HELPIUM_DEMO=thread (or typing) opens a sample conversation, for design review.
            if configured, let demo = environment["HELPIUM_DEMO"] {
                DispatchQueue.main.asyncAfter(deadline: .now() + 2) { Helpium.presentDemo(typing: demo == "typing") }
            }
            if let index = ProcessInfo.processInfo.environment["HELPIUM_THEME"].flatMap(Int.init), Themes.appearances.indices.contains(index) {
                theme = index
                Helpium.setAppearance(Themes.appearances[index])
            }
        }
    }

    private func configure() {
        Helpium.configure(widgetKey: widgetKey, baseURL: URL(string: baseUrl) ?? URL(string: "https://helpium.io")!, appearance: Themes.appearances[theme])
        listener = Helpium.addUnreadCountListener { unread = $0 }
        configured = true
    }
}

/// A few looks to try the appearance API.
enum Themes {
    static let names = ["Default", "Ocean", "Forest", "Midnight (forced dark)", "Sunset (forced light)"]

    static let appearances: [HelpiumAppearance] = [
        HelpiumAppearance(),
        HelpiumAppearance(
            light: HelpiumColors(primary: "#0ea5e9", headerBackground: "#0ea5e9", headerText: "#ffffff", agentBubble: "#e0f2fe", agentBubbleText: "#0c4a6e"),
            dark: HelpiumColors(primary: "#38bdf8", background: "#0b1120", surface: "#172036", headerBackground: "#0c4a6e", headerText: "#e0f2fe")
        ),
        HelpiumAppearance(colors: HelpiumColors(
            primary: "#16a34a", background: "#f7fbf4", surface: "#e6f2df", text: "#14301c", textSecondary: "#4b6b52",
            border: "#cfe3c6", headerBackground: "#14532d", headerText: "#ecfdf5", customerBubble: "#14532d", link: "#15803d"
        )),
        HelpiumAppearance(colors: HelpiumColors(
            primary: "#a78bfa", onPrimary: "#1e1b4b", background: "#0f0d1a", surface: "#1c1830", text: "#ede9fe",
            textSecondary: "#a5a0c8", border: "#2e2850", unreadBadge: "#f472b6"
        ), colorScheme: .dark),
        HelpiumAppearance(colors: HelpiumColors(
            primary: "#ea580c", background: "#fff7ed", surface: "#ffedd5", text: "#431407", border: "#fed7aa",
            headerBackground: "#ea580c", headerText: "#ffffff", customerBubble: "#c2410c", inputBackground: "#ffffff"
        ), colorScheme: .light),
    ]
}
