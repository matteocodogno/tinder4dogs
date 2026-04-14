package com.ai4dev.tinderfordogs.chat.model

import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ChatThreadTest {

    @Test
    fun `should create thread with required fields`() {
        val matchId = UUID.randomUUID()
        val ownerAId = UUID.randomUUID()
        val ownerBId = UUID.randomUUID()

        val thread = ChatThread(matchId = matchId, ownerAId = ownerAId, ownerBId = ownerBId)

        assertEquals(matchId, thread.matchId)
        assertEquals(ownerAId, thread.ownerAId)
        assertEquals(ownerBId, thread.ownerBId)
        assertNull(thread.lastMessagePreview)
        assertNull(thread.lastMessageAt)
        assertNull(thread.id)
        assertNotNull(thread.createdAt)
    }

    @Test
    fun `should allow updating last message preview and timestamp`() {
        val thread = ChatThread(
            matchId = UUID.randomUUID(),
            ownerAId = UUID.randomUUID(),
            ownerBId = UUID.randomUUID(),
        )
        val preview = "Hey, want to meet at the park?"
        val now = Instant.now()

        thread.lastMessagePreview = preview
        thread.lastMessageAt = now

        assertEquals(preview, thread.lastMessagePreview)
        assertEquals(now, thread.lastMessageAt)
    }

    @Test
    fun `should truncate preview representation to 100 chars boundary`() {
        val thread = ChatThread(
            matchId = UUID.randomUUID(),
            ownerAId = UUID.randomUUID(),
            ownerBId = UUID.randomUUID(),
        )
        val exactly100 = "a".repeat(100)

        thread.lastMessagePreview = exactly100

        assertEquals(100, thread.lastMessagePreview!!.length)
    }
}
