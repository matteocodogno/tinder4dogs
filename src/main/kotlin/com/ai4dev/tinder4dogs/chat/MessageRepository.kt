package com.ai4dev.tinder4dogs.chat

import org.springframework.data.jpa.repository.JpaRepository

interface MessageRepository : JpaRepository<Message, Long> {

    /** Full history of a chat in acceptance order: the id is assigned on acceptance, so it is that order. */
    fun findAllByChatIdOrderByIdAsc(chatId: Long): List<Message>

    /** Catch-up fetch for a stream whose watermark is [afterId]: everything accepted after it, in order. */
    fun findAllByChatIdAndIdGreaterThanOrderByIdAsc(chatId: Long, afterId: Long): List<Message>

    /** The newest message of a chat, used to seed the watermark of a fresh stream. Null when the chat has none. */
    fun findFirstByChatIdOrderByIdDesc(chatId: Long): Message?
}
