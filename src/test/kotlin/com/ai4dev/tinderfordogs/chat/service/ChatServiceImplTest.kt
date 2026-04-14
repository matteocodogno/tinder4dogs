package com.ai4dev.tinderfordogs.chat.service

import com.ai4dev.tinderfordogs.chat.model.ChatThread
import com.ai4dev.tinderfordogs.chat.repository.ChatMessageRepository
import com.ai4dev.tinderfordogs.chat.repository.ChatThreadRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.assertEquals

class ChatServiceImplTest {

    private lateinit var chatThreadRepository: ChatThreadRepository
    private lateinit var chatMessageRepository: ChatMessageRepository
    private lateinit var chatService: ChatServiceImpl

    @BeforeEach
    fun setup() {
        chatThreadRepository = mockk()
        chatMessageRepository = mockk()
        chatService = ChatServiceImpl(chatThreadRepository, chatMessageRepository)
    }

    // --- createThread ---

    @Test
    fun `createThread should persist and return a new thread`() {
        val matchId = UUID.randomUUID()
        val ownerAId = UUID.randomUUID()
        val ownerBId = UUID.randomUUID()
        val savedThread = ChatThread(matchId = matchId, ownerAId = ownerAId, ownerBId = ownerBId, id = UUID.randomUUID())

        every { chatThreadRepository.findByMatchId(matchId) } returns null
        every { chatThreadRepository.save(any()) } returns savedThread

        val result = chatService.createThread(matchId, ownerAId, ownerBId)

        assertEquals(savedThread.id, result.id)
        assertEquals(matchId, result.matchId)
        verify(exactly = 1) { chatThreadRepository.save(any()) }
    }

    @Test
    fun `createThread should throw ChatThreadAlreadyExistsException when thread already exists for matchId`() {
        val matchId = UUID.randomUUID()
        val existing = ChatThread(matchId = matchId, ownerAId = UUID.randomUUID(), ownerBId = UUID.randomUUID(), id = UUID.randomUUID())

        every { chatThreadRepository.findByMatchId(matchId) } returns existing

        assertThrows<ChatThreadAlreadyExistsException> {
            chatService.createThread(matchId, UUID.randomUUID(), UUID.randomUUID())
        }
        verify(exactly = 0) { chatThreadRepository.save(any()) }
    }

    // --- deleteThread ---

    @Test
    fun `deleteThread should delete messages then thread in order`() {
        val matchId = UUID.randomUUID()
        val threadId = UUID.randomUUID()
        val thread = ChatThread(matchId = matchId, ownerAId = UUID.randomUUID(), ownerBId = UUID.randomUUID(), id = threadId)

        every { chatThreadRepository.findByMatchId(matchId) } returns thread
        every { chatMessageRepository.deleteAllByThreadId(threadId) } returns Unit
        every { chatThreadRepository.delete(thread) } returns Unit

        chatService.deleteThread(matchId)

        verify(exactly = 1) { chatMessageRepository.deleteAllByThreadId(threadId) }
        verify(exactly = 1) { chatThreadRepository.delete(thread) }
    }

    @Test
    fun `deleteThread should throw ChatThreadNotFoundException when no thread exists for matchId`() {
        val matchId = UUID.randomUUID()

        every { chatThreadRepository.findByMatchId(matchId) } returns null

        assertThrows<ChatThreadNotFoundException> {
            chatService.deleteThread(matchId)
        }
        verify(exactly = 0) { chatMessageRepository.deleteAllByThreadId(any()) }
        verify(exactly = 0) { chatThreadRepository.delete(any<ChatThread>()) }
    }
}
