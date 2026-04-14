package com.ai4dev.tinderfordogs.chat.service

import com.ai4dev.tinderfordogs.chat.model.ChatThread
import com.ai4dev.tinderfordogs.chat.repository.ChatMessageRepository
import com.ai4dev.tinderfordogs.chat.repository.ChatThreadRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Service
class ChatServiceImpl(
    private val chatThreadRepository: ChatThreadRepository,
    private val chatMessageRepository: ChatMessageRepository,
) : ChatService {

    @Transactional
    override fun createThread(matchId: UUID, ownerAId: UUID, ownerBId: UUID): ChatThread {
        chatThreadRepository.findByMatchId(matchId)?.let {
            throw ChatThreadAlreadyExistsException(matchId)
        }
        val thread = ChatThread(matchId = matchId, ownerAId = ownerAId, ownerBId = ownerBId)
        return chatThreadRepository.save(thread).also {
            logger.info { "Created chat thread ${it.id} for matchId=$matchId" }
        }
    }

    @Transactional
    override fun deleteThread(matchId: UUID) {
        val thread = chatThreadRepository.findByMatchId(matchId)
            ?: throw ChatThreadNotFoundException(matchId)
        chatMessageRepository.deleteAllByThreadId(thread.id!!)
        chatThreadRepository.delete(thread)
        logger.info { "Deleted chat thread ${thread.id} for matchId=$matchId" }
    }
}
