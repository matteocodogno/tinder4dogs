package com.ai4dev.tinderfordogs.chat.service

import com.ai4dev.tinderfordogs.chat.model.ChatThread
import java.util.UUID

interface ChatService {
    fun createThread(matchId: UUID, ownerAId: UUID, ownerBId: UUID): ChatThread
    fun deleteThread(matchId: UUID)
}
