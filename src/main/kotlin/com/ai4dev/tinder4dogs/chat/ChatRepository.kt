package com.ai4dev.tinder4dogs.chat

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ChatRepository : JpaRepository<Chat, Long> {

    /** Canonical pair lookup: the pair is always stored as `(min, max)`, so this is the identity of a chat. */
    fun findByDogAIdAndDogBId(a: Long, b: Long): Chat?

    /**
     * Takes a PostgreSQL advisory lock keyed on the chat id, serializing sends within one chat.
     *
     * The lock is transaction-scoped: it is held until the surrounding transaction commits or rolls
     * back, and is never released explicitly.
     */
    @Query(value = "SELECT pg_advisory_xact_lock(:chatId)", nativeQuery = true)
    fun lockById(@Param("chatId") chatId: Long)

    /** Every chat the dog takes part in, on either side, most recently active first. */
    @Query(
        """
        SELECT c FROM Chat c
        WHERE c.dogAId = :dogId OR c.dogBId = :dogId
        ORDER BY coalesce(c.lastMessageAt, c.createdAt) DESC
        """,
    )
    fun findAllByParticipantOrderByLastActivity(@Param("dogId") dogId: Long): List<Chat>
}
