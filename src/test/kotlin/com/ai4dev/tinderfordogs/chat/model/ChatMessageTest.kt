package com.ai4dev.tinderfordogs.chat.model

import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ChatMessageTest {

    @Test
    fun `should create message with required fields`() {
        val threadId = UUID.randomUUID()
        val senderOwnerId = UUID.randomUUID()
        val content = "Hello!"

        val message = ChatMessage(threadId = threadId, senderOwnerId = senderOwnerId, content = content)

        assertEquals(threadId, message.threadId)
        assertEquals(senderOwnerId, message.senderOwnerId)
        assertEquals(content, message.content)
        assertNull(message.id)
        assertNotNull(message.sentAt)
    }

    @Test
    fun `should accept content at exact 2000 character boundary`() {
        val content = "x".repeat(2000)

        val message = ChatMessage(
            threadId = UUID.randomUUID(),
            senderOwnerId = UUID.randomUUID(),
            content = content,
        )

        assertEquals(2000, message.content.length)
    }
}
