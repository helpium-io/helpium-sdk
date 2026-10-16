package io.helpium.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceTest {
    @Test
    fun defaultsFollowTheWorkspaceColor() {
        val palette = Palette.resolve(HelpiumAppearance(), "#0ea5e9", dark = false)
        assertEquals(Palette.parse("#0ea5e9"), palette.primary)
        assertEquals(palette.primary, palette.customerBubble)
        assertEquals(palette.primary, palette.link)
        assertEquals(Palette.parse("#FFFFFF"), palette.background)
    }

    @Test
    fun derivedColorsFollowTheirBase() {
        val colors = HelpiumColors().primary("#111111").background("#fafafa").surface("#eeeeee").text("#222222")
        val palette = Palette.resolve(HelpiumAppearance().colors(colors), "#0ea5e9", dark = false)
        assertEquals(Palette.parse("#111111"), palette.primary)
        assertEquals(0xFFFFFFFF.toInt(), palette.onPrimary)
        assertEquals(Palette.parse("#fafafa"), palette.headerBackground)
        assertEquals(Palette.parse("#eeeeee"), palette.agentBubble)
        assertEquals(Palette.parse("#222222"), palette.inputText)
    }

    @Test
    fun darkModeUsesTheDarkSet() {
        val appearance = HelpiumAppearance()
            .light(HelpiumColors().background("#ffffff"))
            .dark(HelpiumColors().background("#000000").agentBubble("#333333"))
        val palette = Palette.resolve(appearance, null, dark = true)
        assertEquals(Palette.parse("#000000"), palette.background)
        assertEquals(Palette.parse("#333333"), palette.agentBubble)
        assertTrue(palette.headerIsDark)
    }

    @Test
    fun parsesShortAndTransparentHex() {
        assertEquals(0xFFFF0000.toInt(), Palette.parse("#f00"))
        assertEquals(0x80FF0000.toInt(), Palette.parse("#FF000080"))
    }
}
