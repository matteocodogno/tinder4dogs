package com.ai4dev.tinder4dogs.chat

import com.ai4dev.tinder4dogs.dog.DogRepository
import java.time.Instant
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Owns the domain rules of a chat.
 *
 * For now that is creation only: the rest of the surface (messaging, history, discovery) arrives
 * with the tasks that need it.
 */
@Service
class ChatService(
    private val dogs: DogRepository,
    private val chats: ChatRepository,
) {

    /**
     * Makes sure a chat exists for a freshly matched pair, exactly once.
     *
     * The pair is stored canonically as `(min, max)`, so the same two dogs in either order name the
     * same chat. Idempotency is lookup-first; the `uq_chat_pair` unique constraint is the backstop
     * for two events racing each other, and losing that race means re-fetching what the winner
     * wrote rather than failing.
     *
     * Invalid events are logged at WARN and produce nothing: the match that triggered this is
     * already committed, and it must not be brought down by a chat that declined to exist. This is
     * the deliberate, design-sanctioned deviation from the `require` convention — see
     * [CreateChatEvent].
     *
     * @return the chat for the pair, or `null` if the event was rejected.
     */
    @EventListener
    @Transactional
    fun onMatched(event: CreateChatEvent): Chat? {
        val (dogAId, dogBId) = event
        if (dogAId == dogBId) {
            log.warn("ignoring {}: a dog cannot match itself", event)
            return null
        }
        val missing = listOf(dogAId, dogBId).filterNot(dogs::existsById)
        if (missing.isNotEmpty()) {
            log.warn("ignoring {}: no such dog(s) {}", event, missing)
            return null
        }

        val first = minOf(dogAId, dogBId)
        val second = maxOf(dogAId, dogBId)

        chats.findByDogAIdAndDogBId(first, second)?.let { return it }

        return try {
            chats.save(
                Chat(
                    dogAId = first,
                    dogBId = second,
                    createdAt = Instant.now(),
                ),
            )
        } catch (violation: DataIntegrityViolationException) {
            log.warn(
                "chat for dogs {} and {} was created concurrently, reusing it: {}",
                first,
                second,
                violation.message,
            )
            chats.findByDogAIdAndDogBId(first, second)
        }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(ChatService::class.java)
    }
}
