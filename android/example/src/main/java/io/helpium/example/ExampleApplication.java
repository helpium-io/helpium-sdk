package io.helpium.example;

import android.app.Application;

import io.helpium.sdk.Helpium;

public class ExampleApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Helpium.configure(this, BuildConfig.HELPIUM_WIDGET_KEY, BuildConfig.HELPIUM_BASE_URL);
    }
}
