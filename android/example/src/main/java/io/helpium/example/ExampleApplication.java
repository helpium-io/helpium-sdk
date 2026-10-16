package io.helpium.example;

import android.app.Application;

import io.helpium.sdk.Helpium;
import io.helpium.sdk.HelpiumAppearance;
import io.helpium.sdk.HelpiumColors;

public class ExampleApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Every color is optional; unset ones follow the workspace brand color and neutral tones.
        Helpium.setAppearance(new HelpiumAppearance()
                .light(new HelpiumColors()
                        .primary("#0ea5e9")
                        .background("#ffffff")
                        .agentBubble("#f1f5f9")
                        .headerBackground("#0ea5e9")
                        .headerText("#ffffff"))
                .dark(new HelpiumColors()
                        .primary("#38bdf8")
                        .background("#0f172a")
                        .surface("#1e293b")
                        .text("#f1f5f9")));

        Helpium.configure(this, BuildConfig.HELPIUM_WIDGET_KEY, BuildConfig.HELPIUM_BASE_URL);
    }
}
