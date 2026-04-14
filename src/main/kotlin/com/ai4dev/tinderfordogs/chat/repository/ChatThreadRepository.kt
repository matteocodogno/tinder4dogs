package com.ai4dev.tinderfordogs.chat.repository

import com.ai4dev.tinderfordogs.chat.model.ChatThread
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ChatThreadRepository : JpaRepository<ChatThread, UUID> {
    fun findByMatchId(matchId: UUID): ChatThread?
    fun findAllByOwnerAIdOrOwnerBId(ownerAId: UUID, ownerBId: UUID): List<ChatThread>
}
