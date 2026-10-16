package io.helpium.sdk

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper

/**
 * The Helpium mobile SDK: native in-app chat with your support team and AI.
 *
 * Java:
 * ```java
 * Helpium.configure(this, "wk_...");
 * Helpium.identify(userJwtFromYourServer);
 * Helpium.present(activity);
 * ```
 */
object Helpium {
    /** Listener for the unread count, usable from Java. */
    fun interface UnreadCountListener {
        fun onUnreadCountChanged(count: Int)
    }

    internal var store: ChatStore? = null
        private set
    private var appearance = HelpiumAppearance()
    private val unreadListeners = linkedSetOf<UnreadCountListener>()
    private val main = Handler(Looper.getMainLooper())

    /** Call once, e.g. in `Application.onCreate`, with the workspace widget key (Helpium > Channels > Live Chat). */
    @JvmStatic
    @JvmOverloads
    fun configure(context: Context, widgetKey: String, baseUrl: String = "https://helpium.io") {
        val store = ChatStore(context, Api(baseUrl, widgetKey))
        store.explicitLocale = Strings.forcedLocale
        store.appearance = appearance
        store.onUnreadCountChange = { count -> unreadListeners.toList().forEach { it.onUnreadCountChanged(count) } }
        this.store = store
        store.start()
    }

    /**
     * A verified user: `userJwt` is an HS256 token your server signs with the
     * workspace identity secret (`user_id`, `email`, `exp`). The conversation
     * follows the user across devices and AI actions can act on their account.
     * Pass `email` when the token has no `email` claim.
     */
    @JvmStatic
    @JvmOverloads
    fun identify(userJwt: String, email: String? = null, name: String? = null) = onMain {
        requireStore().setIdentity(Identity(userJwt = userJwt, email = email, name = name))
    }

    /** An unverified user, known only by what they told your app. */
    @JvmStatic
    @JvmOverloads
    fun identifyByEmail(email: String, name: String? = null) = onMain {
        requireStore().setIdentity(Identity(email = email, name = name))
    }

    /** Call when the user signs out: stops push to this phone and hides their conversation. */
    @JvmStatic
    fun logout() = onMain { requireStore().logout() }

    /** Forces the chat language (`"en"`, `"es"`); `null` follows the phone. Call before `configure`. */
    @JvmStatic
    fun setLocale(code: String?) {
        Strings.forcedLocale = code
        store?.explicitLocale = code
    }

    /**
     * Every color of the chat, for light and dark mode. Call before or after
     * `configure`; it applies right away, even while the chat is open.
     *
     * ```java
     * Helpium.setAppearance(new HelpiumAppearance()
     *     .light(new HelpiumColors().primary("#0ea5e9").background("#ffffff").agentBubble("#f1f5f9"))
     *     .dark(new HelpiumColors().primary("#38bdf8").background("#0f172a").agentBubble("#1e293b")));
     * ```
     */
    @JvmStatic
    fun setAppearance(appearance: HelpiumAppearance) = onMain {
        this.appearance = appearance
        store?.appearance = appearance
    }

    /** Opens the chat. */
    @JvmStatic
    fun present(context: Context) {
        requireStore()
        val intent = Intent(context, HelpiumActivity::class.java)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Agent and bot messages the customer has not seen yet. */
    @JvmStatic
    val unreadCount: Int get() = store?.unreadCount ?: 0

    /** Called on the main thread whenever the unread count changes. */
    @JvmStatic
    fun addUnreadCountListener(listener: UnreadCountListener) {
        unreadListeners += listener
        listener.onUnreadCountChanged(unreadCount)
    }

    @JvmStatic
    fun removeUnreadCountListener(listener: UnreadCountListener) {
        unreadListeners -= listener
    }

    // region Push

    /** Pass the FCM registration token (from `FirebaseMessaging.getToken()` and `onNewToken`). */
    @JvmStatic
    fun setPushToken(token: String) = onMain { requireStore().setPushToken(token) }

    /** Whether a push (`RemoteMessage.getData()`) was sent by Helpium. */
    @JvmStatic
    fun isHelpiumPush(data: Map<String, String>?): Boolean = data?.get("helpium") == "1"

    /** Whether a notification tap's extras (`getIntent().getExtras()`) belong to Helpium. */
    @JvmStatic
    fun isHelpiumPush(extras: Bundle?): Boolean = extras?.getString("helpium") == "1"

    /** Whether to show a Helpium push that arrives in the foreground: not while the chat is open. */
    @JvmStatic
    fun shouldShowNotification(data: Map<String, String>?): Boolean = isHelpiumPush(data) && store?.isVisible != true

    /** Opens the conversation a push is about. Returns false when it is not Helpium's. */
    @JvmStatic
    fun handlePush(context: Context, data: Map<String, String>?): Boolean {
        if (!isHelpiumPush(data)) return false
        open(context, data?.get("conversation_token"))
        return true
    }

    /**
     * Opens the conversation a tapped notification is about. When the app was
     * in the background Android shows the notification itself and the tap
     * lands in your launcher activity: call this with `getIntent().getExtras()`.
     */
    @JvmStatic
    fun handlePush(context: Context, extras: Bundle?): Boolean {
        if (!isHelpiumPush(extras)) return false
        open(context, extras?.getString("conversation_token"))
        return true
    }

    private fun open(context: Context, conversationToken: String?) = onMain {
        conversationToken?.let { requireStore().open(it) }
        present(context)
    }

    // endregion

    private fun requireStore(): ChatStore =
        store ?: throw IllegalStateException("Call Helpium.configure(context, widgetKey) before using the SDK.")

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }
}
