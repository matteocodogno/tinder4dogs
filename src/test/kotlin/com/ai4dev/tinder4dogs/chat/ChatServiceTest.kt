package com.ai4dev.tinder4dogs.chat

import com.ai4dev.tinder4dogs.dog.DogRepository
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.dao.DataIntegrityViolationException

class ChatServiceTest {

    private val dogs = mock(DogRepository::class.java)
    private val chats = mock(ChatRepository::class.java)
    private val service = ChatService(dogs, chats)

    private val rexId = 7L
    private val bellaId = 3L

    /**
     * Mockito's matchers return `null`, which Kotlin refuses to pass where a non-null type is
     * declared. Erasing the cast through a type parameter is the usual way around it.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> anyValue(): T = Mockito.any<T>() as T

    private fun dogExists(id: Long) {
        Mockito.`when`(dogs.existsById(id)).thenReturn(true)
    }

    private fun savedChatGetsId(id: Long) {
        Mockito.`when`(chats.save(anyValue<Chat>())).thenAnswer { invocation ->
            invocation.getArgument<Chat>(0).apply { this.id = id }
        }
    }

    private fun chatOfThePair(id: Long) = Chat(
        id = id,
        dogAId = bellaId,
        dogBId = rexId,
        createdAt = Instant.parse("2024-01-01T00:00:00Z"),
    )

    @Test
    fun `creating a chat for a matched pair stores the pair in canonical order`() {
        dogExists(rexId)
        dogExists(bellaId)
        Mockito.`when`(chats.findByDogAIdAndDogBId(bellaId, rexId)).thenReturn(null)
        savedChatGetsId(1L)
        val before = Instant.now()

        // the event names the higher id first: the service, not the emitter, orders the pair
        val created = service.onMatched(CreateChatEvent(rexId, bellaId))

        val saved = ArgumentCaptor.forClass(Chat::class.java)
        verify(chats).save(saved.capture())
        assertThat(saved.value.dogAId).isEqualTo(bellaId)
        assertThat(saved.value.dogBId).isEqualTo(rexId)
        assertThat(saved.value.createdAt).isBetween(before, Instant.now())
        assertThat(saved.value.lastMessageAt).isNull()
        verify(chats).findByDogAIdAndDogBId(bellaId, rexId)
        assertThat(created?.id).isEqualTo(1L)
    }

    @Test
    fun `a duplicate create chat event returns the existing chat instead of saving a second one`() {
        dogExists(rexId)
        dogExists(bellaId)
        val existing = chatOfThePair(42L)
        Mockito.`when`(chats.findByDogAIdAndDogBId(bellaId, rexId)).thenReturn(existing)

        val result = service.onMatched(CreateChatEvent(rexId, bellaId))

        assertThat(result).isSameAs(existing)
        verify(chats, never()).save(anyValue<Chat>())
    }

    @Test
    fun `a create chat event for an unknown dog is rejected without saving`() {
        val unknownId = 99L
        dogExists(rexId)
        Mockito.`when`(dogs.existsById(unknownId)).thenReturn(false)

        val result = service.onMatched(CreateChatEvent(rexId, unknownId))

        assertThat(result).isNull()
        verify(chats, never()).save(anyValue<Chat>())
    }

    @Test
    fun `a create chat event naming the same dog twice is rejected without saving`() {
        dogExists(rexId)

        val result = service.onMatched(CreateChatEvent(rexId, rexId))

        assertThat(result).isNull()
        verify(chats, never()).save(anyValue<Chat>())
    }

    @Test
    fun `a create chat event that loses the insert race returns the chat the winner created`() {
        dogExists(rexId)
        dogExists(bellaId)
        val winner = chatOfThePair(42L)
        Mockito.`when`(chats.findByDogAIdAndDogBId(bellaId, rexId))
            .thenReturn(null)
            .thenReturn(winner)
        Mockito.`when`(chats.save(anyValue<Chat>()))
            .thenThrow(DataIntegrityViolationException("uq_chat_pair"))

        val result = service.onMatched(CreateChatEvent(rexId, bellaId))

        assertThat(result).isSameAs(winner)
    }
}
