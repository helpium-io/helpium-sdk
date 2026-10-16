package io.helpium.sdk

import android.content.Context

/**
 * Remembers the conversation token across launches, scoped per widget key and
 * per signed-in user, so a shared phone never shows one user's conversation
 * to another.
 */
internal class Storage(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("io.helpium.sdk", Context.MODE_PRIVATE)

    fun conversationToken(widgetKey: String, userKey: String?): String? = prefs.getString(key(widgetKey, userKey), null)

    fun setConversationToken(token: String?, widgetKey: String, userKey: String?) {
        prefs.edit().apply { if (token == null) remove(key(widgetKey, userKey)) else putString(key(widgetKey, userKey), token) }.apply()
    }

    private fun key(widgetKey: String, userKey: String?) = "conversation:$widgetKey:${userKey ?: "anonymous"}"
}
