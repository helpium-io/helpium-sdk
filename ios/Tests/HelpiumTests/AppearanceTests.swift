import SwiftUI
import XCTest
@testable import Helpium

final class AppearanceTests: XCTestCase {
    func testDefaultsFollowTheWorkspaceColor() {
        let palette = Palette.resolve(HelpiumAppearance(), workspacePrimary: "#0ea5e9", dark: false)
        XCTAssertEqual(palette.primary, Color(hex: "#0ea5e9"))
        XCTAssertEqual(palette.customerBubble, palette.primary)
        XCTAssertEqual(palette.link, palette.primary)
        XCTAssertEqual(palette.background, Color(hex: "#FFFFFF"))
    }

    func testDerivedColorsFollowTheirBase() {
        let colors = HelpiumColors(primary: "#111111", background: "#fafafa", surface: "#eeeeee", text: "#222222")
        let palette = Palette.resolve(HelpiumAppearance(colors: colors), workspacePrimary: "#0ea5e9", dark: false)
        XCTAssertEqual(palette.primary, Color(hex: "#111111"))
        XCTAssertEqual(palette.onPrimary, .white)
        XCTAssertEqual(palette.headerBackground, Color(hex: "#fafafa"))
        XCTAssertEqual(palette.agentBubble, Color(hex: "#eeeeee"))
        XCTAssertEqual(palette.inputText, Color(hex: "#222222"))
    }

    func testDarkModeUsesTheDarkSet() {
        let appearance = HelpiumAppearance(light: .init(background: "#ffffff"), dark: .init(background: "#000000", agentBubble: "#333333"))
        let palette = Palette.resolve(appearance, workspacePrimary: nil, dark: true)
        XCTAssertEqual(palette.background, Color(hex: "#000000"))
        XCTAssertEqual(palette.agentBubble, Color(hex: "#333333"))
        XCTAssertTrue(palette.headerIsDark)
    }

    func testExplicitOverridesWin() {
        let colors = HelpiumColors(primary: "#ffffff", onPrimary: "#ff0000", customerBubble: "#00ff00", customerBubbleText: "#0000ff")
        let palette = Palette.resolve(HelpiumAppearance(colors: colors), workspacePrimary: nil, dark: false)
        XCTAssertEqual(palette.onPrimary, Color(hex: "#ff0000"))
        XCTAssertEqual(palette.customerBubble, Color(hex: "#00ff00"))
        XCTAssertEqual(palette.customerBubbleText, Color(hex: "#0000ff"))
    }
}
