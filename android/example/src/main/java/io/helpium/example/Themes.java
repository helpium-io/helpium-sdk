package io.helpium.example;

import io.helpium.sdk.HelpiumAppearance;
import io.helpium.sdk.HelpiumColorScheme;
import io.helpium.sdk.HelpiumColors;

/** A few looks to try the appearance API. */
final class Themes {
    private Themes() {}

    static final String[] NAMES = {"Default", "Ocean", "Forest", "Midnight (forced dark)", "Sunset (forced light)"};

    static final HelpiumAppearance[] APPEARANCES = {
            new HelpiumAppearance(),

            new HelpiumAppearance()
                    .light(new HelpiumColors()
                            .primary("#0ea5e9")
                            .headerBackground("#0ea5e9")
                            .headerText("#ffffff")
                            .agentBubble("#e0f2fe")
                            .agentBubbleText("#0c4a6e"))
                    .dark(new HelpiumColors()
                            .primary("#38bdf8")
                            .background("#0b1120")
                            .surface("#172036")
                            .headerBackground("#0c4a6e")
                            .headerText("#e0f2fe")),

            new HelpiumAppearance().colors(new HelpiumColors()
                    .primary("#16a34a")
                    .background("#f7fbf4")
                    .surface("#e6f2df")
                    .text("#14301c")
                    .textSecondary("#4b6b52")
                    .border("#cfe3c6")
                    .headerBackground("#14532d")
                    .headerText("#ecfdf5")
                    .customerBubble("#14532d")
                    .link("#15803d")),

            new HelpiumAppearance()
                    .colors(new HelpiumColors()
                            .primary("#a78bfa")
                            .onPrimary("#1e1b4b")
                            .background("#0f0d1a")
                            .surface("#1c1830")
                            .text("#ede9fe")
                            .textSecondary("#a5a0c8")
                            .border("#2e2850")
                            .unreadBadge("#f472b6"))
                    .colorScheme(HelpiumColorScheme.DARK),

            new HelpiumAppearance()
                    .colors(new HelpiumColors()
                            .primary("#ea580c")
                            .background("#fff7ed")
                            .surface("#ffedd5")
                            .text("#431407")
                            .headerBackground("#ea580c")
                            .headerText("#ffffff")
                            .customerBubble("#c2410c")
                            .inputBackground("#ffffff")
                            .border("#fed7aa"))
                    .colorScheme(HelpiumColorScheme.LIGHT),
    };
}
