package io.helpium.sdk

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parses the shared fixtures in spec/fixtures, the same ones the iOS tests use. */
class ParsingTest {
    private fun fixture(name: String) = JSONObject(javaClass.classLoader!!.getResource("$name.json").readText())

    @Test
    fun parsesWidgetSettings() {
        val settings = WidgetSettings.parse(fixture("settings").getJSONObject("data"))
        assertEquals("Orbi", settings.botName)
        assertEquals("abc", settings.websocket.appKey)
        assertEquals(12, settings.instantAnswers.first().id)
        assertTrue(settings.conversationRatingsEnabled)
    }

    @Test
    fun parsesConversationRolesAndInteractive() {
        val conversation = Conversation.parse(fixture("conversation").getJSONObject("data"))
        val messages = conversation.messages!!
        assertEquals("Ana", conversation.assignedAgent?.name)
        assertEquals(Role.CUSTOMER, messages[0].role)
        assertEquals(Role.BOT, messages[1].role)
        assertTrue(messages[1].shouldHandoff)
        assertEquals(Interactive.Suggestions(listOf("Yes", "No")), messages[1].interactive)
        assertTrue(messages[1].attachments.first().isImage)
        assertEquals("Shipping", messages[1].sources.first().title)
    }

    @Test
    fun socketSenderTypeUserIsAnAgent() {
        val message = ChatMessage.fromEvent(fixture("new_message_event"))
        assertEquals(Role.AGENT, message.role)
        assertEquals("Ana", message.senderName)
    }

    @Test
    fun readsClaimsFromTheIdentityToken() {
        val identity = Identity(userJwt = "eyJhbGciOiJIUzI1NiJ9.eyJ1c2VyX2lkIjo0MiwiZW1haWwiOiJqYW5lQGV4YW1wbGUuY29tIn0.sig")
        assertEquals("42", identity.jwtClaim("user_id"))
        assertEquals("jane@example.com", identity.jwtClaim("email"))
        assertEquals("42", identity.storageKey)
        assertNull(Identity().storageKey)
    }
}
