package io.helpium.example;

import android.app.Application;
import android.util.Log;

import io.helpium.sdk.Helpium;

public class ExampleApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        if (!BuildConfig.LOCAL_HOST.isEmpty() || !BuildConfig.LOCAL_CA_BASE64.isEmpty()) {
            try {
                Helpium.setHttpClient(LocalServer.client(BuildConfig.LOCAL_HOST, BuildConfig.LOCAL_CA_BASE64));
            } catch (Exception e) {
                Log.e("HelpiumExample", "Local server setup failed", e);
            }
        }

        Helpium.setAppearance(Themes.APPEARANCES[0]);
        Helpium.configure(this, BuildConfig.HELPIUM_WIDGET_KEY, BuildConfig.HELPIUM_BASE_URL);
    }
}
