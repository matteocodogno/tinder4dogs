package com.ai4dev.tinderfordogs.support.service

import com.ai4dev.tinderfordogs.ai.common.model.ChatRequest
import com.ai4dev.tinderfordogs.ai.common.model.Message
import com.ai4dev.tinderfordogs.ai.common.service.LiteLLMService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import kotlin.collections.plus

private val logger = KotlinLogging.logger {}

@Service
class SupportAssistantService(
    private val llm: LiteLLMService,
    @Value($$"${tinder4dogs.support.model}") private val model: String,
) {
    suspend fun answer(
        userMessage: String,
        conversationHistory: List<Message> = emptyList(),
    ): String {
        logger.info { "Generating response for user message: $userMessage" }
        val messages = conversationHistory + Message("user", userMessage)

        return llm
            .chat(ChatRequest(model, messages))
            .choices
            .first()
            .message.content
    }
}
