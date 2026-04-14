package com.ai4dev.tinderfordogs.chat.repository

import com.ai4dev.tinderfordogs.chat.model.ChatMessage
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ChatMessageRepository : JpaRepository<ChatMessage, UUID> {
    fun findAllByThreadIdOrderBySentAtAsc(threadId: UUID, pageable: Pageable): Page<ChatMessage>

    @Modifying
    @Query("DELETE FROM ChatMessage m WHERE m.threadId = :threadId")
    fun deleteAllByThreadId(threadId: UUID)
}
