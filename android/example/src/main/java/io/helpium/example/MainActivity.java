package io.helpium.example;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;

import io.helpium.sdk.Helpium;

/** How a Java app integrates the SDK: one button, the unread badge and push taps. */
public class MainActivity extends Activity {
    private Button supportButton;

    private final Helpium.UnreadCountListener unreadListener = count ->
            supportButton.setText(count > 0 ? "Chat with support (" + count + ")" : "Chat with support");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        supportButton = new Button(this);
        supportButton.setOnClickListener(view -> Helpium.present(this));

        LinearLayout root = new LinearLayout(this);
        root.setGravity(Gravity.CENTER);
        root.addView(supportButton);
        setContentView(root);

        Helpium.addUnreadCountListener(unreadListener);

        // A notification tapped while the app was in the background lands here.
        Helpium.handlePush(this, getIntent().getExtras());
    }

    @Override
    protected void onDestroy() {
        Helpium.removeUnreadCountListener(unreadListener);
        super.onDestroy();
    }
}
