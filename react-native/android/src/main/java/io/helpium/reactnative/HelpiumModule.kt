package io.helpium.reactnative

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import io.helpium.sdk.Helpium
import io.helpium.sdk.HelpiumAppearance
import io.helpium.sdk.HelpiumColorScheme
import io.helpium.sdk.HelpiumColors

/** React Native bridge over the Helpium Android SDK (compiled into this library). */
class HelpiumModule(private val context: ReactApplicationContext) : ReactContextBaseJavaModule(context) {
    private val unreadListener = Helpium.UnreadCountListener { count ->
        if (context.hasActiveReactInstance()) {
            context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("HelpiumUnreadCountChanged", Arguments.createMap().apply { putInt("count", count) })
        }
    }

    override fun getName() = "HelpiumReactNative"

    @ReactMethod
    fun configure(widgetKey: String, baseUrl: String?) {
        context.runOnUiQueueThread {
            if (baseUrl != null) Helpium.configure(context, widgetKey, baseUrl) else Helpium.configure(context, widgetKey)
            Helpium.addUnreadCountListener(unreadListener)
        }
    }

    @ReactMethod
    fun setAppearance(appearance: ReadableMap) = Helpium.setAppearance(appearance.toAppearance())

    private fun ReadableMap.toAppearance(): HelpiumAppearance {
        fun colors(key: String): HelpiumColors {
            val map = if (hasKey(key) && !isNull(key)) getMap(key) else null
            fun v(name: String): String? = if (map != null && map.hasKey(name) && !map.isNull(name)) map.getString(name) else null
            return HelpiumColors()
                .primary(v("primary")).onPrimary(v("onPrimary")).background(v("background")).surface(v("surface"))
                .text(v("text")).textSecondary(v("textSecondary")).border(v("border"))
                .headerBackground(v("headerBackground")).headerText(v("headerText"))
                .customerBubble(v("customerBubble")).customerBubbleText(v("customerBubbleText"))
                .agentBubble(v("agentBubble")).agentBubbleText(v("agentBubbleText"))
                .inputBackground(v("inputBackground")).inputText(v("inputText")).link(v("link"))
                .unreadBadge(v("unreadBadge")).unreadBadgeText(v("unreadBadgeText"))
        }
        val scheme = when (if (hasKey("colorScheme") && !isNull("colorScheme")) getString("colorScheme") else null) {
            "light" -> HelpiumColorScheme.LIGHT
            "dark" -> HelpiumColorScheme.DARK
            else -> HelpiumColorScheme.SYSTEM
        }
        return HelpiumAppearance(colors("light"), colors("dark"), scheme)
    }

    @ReactMethod
    fun identify(userJwt: String, email: String?, name: String?) = Helpium.identify(userJwt, email, name)

    @ReactMethod
    fun identifyByEmail(email: String, name: String?) = Helpium.identifyByEmail(email, name)

    @ReactMethod
    fun logout() = Helpium.logout()

    @ReactMethod
    fun setLocale(locale: String?) = Helpium.setLocale(locale)

    @ReactMethod
    fun present() {
        context.runOnUiQueueThread { Helpium.present(currentActivity ?: context) }
    }

    @ReactMethod
    fun setPushToken(token: String) = Helpium.setPushToken(token)

    @ReactMethod
    fun handlePush(data: ReadableMap, promise: Promise) {
        context.runOnUiQueueThread { promise.resolve(Helpium.handlePush(currentActivity ?: context, data.toStringMap())) }
    }

    @ReactMethod
    fun shouldShowNotification(data: ReadableMap, promise: Promise) {
        promise.resolve(Helpium.shouldShowNotification(data.toStringMap()))
    }

    @ReactMethod
    fun getUnreadCount(promise: Promise) {
        promise.resolve(Helpium.unreadCount)
    }

    /** Required by NativeEventEmitter. */
    @ReactMethod
    fun addListener(eventName: String) = Unit

    @ReactMethod
    fun removeListeners(count: Double) = Unit

    private fun ReadableMap.toStringMap(): Map<String, String> =
        toHashMap().mapNotNull { (key, value) -> value?.let { key to it.toString() } }.toMap()
}
