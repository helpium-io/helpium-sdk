package io.helpium.reactnative

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import io.helpium.sdk.Helpium

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
