package io.helpium.example;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import io.helpium.sdk.Helpium;
import io.helpium.sdk.HelpiumDemo;

/** How a Java app integrates the SDK: a support button, the unread badge, identity, colors and push taps. */
public class MainActivity extends Activity {
    private Button supportButton;
    private Button themeButton;
    private int theme = 0;

    private final Helpium.UnreadCountListener unreadListener = count ->
            supportButton.setText(count > 0 ? "Chat with support (" + count + ")" : "Chat with support");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        root.setPadding(padding, padding * 3, padding, padding);

        TextView title = new TextView(this);
        title.setText("Helpium SDK example");
        title.setTextSize(22);
        title.setPadding(0, 0, 0, padding);
        root.addView(title);

        supportButton = new Button(this);
        supportButton.setOnClickListener(view -> Helpium.present(this));
        root.addView(supportButton);

        themeButton = new Button(this);
        themeButton.setText("Colors: " + Themes.NAMES[theme]);
        themeButton.setOnClickListener(view -> {
            theme = (theme + 1) % Themes.APPEARANCES.length;
            Helpium.setAppearance(Themes.APPEARANCES[theme]);
            themeButton.setText("Colors: " + Themes.NAMES[theme]);
        });
        root.addView(themeButton);

        EditText email = new EditText(this);
        email.setHint("Customer email (optional)");
        email.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        root.addView(email);

        Button identify = new Button(this);
        identify.setText("Identify (unverified)");
        identify.setOnClickListener(view -> {
            String value = email.getText().toString().trim();
            if (!value.isEmpty()) Helpium.identifyByEmail(value, null);
        });
        root.addView(identify);

        Button logout = new Button(this);
        logout.setText("Log out");
        logout.setOnClickListener(view -> Helpium.logout());
        root.addView(logout);

        setContentView(root);

        Helpium.addUnreadCountListener(unreadListener);

        // A notification tapped while the app was in the background lands here.
        Helpium.handlePush(this, getIntent().getExtras());

        // adb shell am start -n io.helpium.example/.MainActivity -e demo thread  (or typing): a sample conversation.
        String demo = getIntent().getStringExtra("demo");
        if (demo != null) {
            root.postDelayed(() -> HelpiumDemo.present(this, demo.equals("typing")), 2500);
        }
    }

    @Override
    protected void onDestroy() {
        Helpium.removeUnreadCountListener(unreadListener);
        super.onDestroy();
    }
}
