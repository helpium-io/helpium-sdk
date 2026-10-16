package io.helpium.sdk

import android.content.Context

/**
 * Opens the chat with a sample conversation, for screenshots and design review
 * in the example app. Not part of the public API: it may change at any time.
 */
object HelpiumDemo {
    @JvmStatic
    @JvmOverloads
    fun present(context: Context, typing: Boolean = false) {
        val store = Helpium.store ?: return
        val time = ChatStore.now()
        listOf(
            """{"id":900001,"sender_type":"customer","content":"Hi! My order #1042 hasn't arrived yet","created_at":"$time"}""",
            """{"id":900002,"sender_type":"bot","content":"Sorry about that! I checked **order #1042**: it left our warehouse yesterday and arrives tomorrow.","created_at":"$time"}""",
            """{"id":900003,"sender_type":"bot","content":"You can follow it here: [tracking link](https://example.com/track)","created_at":"$time","should_handoff":true}""",
            """{"id":900004,"sender_type":"customer","content":"Great, thanks!","created_at":"$time"}""",
            """{"id":900005,"sender_type":"customer","content":"Can I change the delivery address?","created_at":"$time"}""",
            """{"id":900006,"sender_type":"user","sender_name":"Ana","content":"Hi! I'm Ana from the support team. Sure, what's the new address?","created_at":"$time"}""",
        ).forEach { store.handle("App\\Events\\NewMessage", it) }
        if (typing) store.handle("App\\Events\\BotTyping", """{"conversation_id":1,"is_typing":true}""")
        store.openThread()
        Helpium.present(context)
    }
}
