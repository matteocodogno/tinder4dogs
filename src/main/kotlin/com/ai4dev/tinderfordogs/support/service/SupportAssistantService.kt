package com.ai4dev.tinderfordogs.support.service

import com.ai4dev.tinderfordogs.ai.common.service.ChatRequest
import com.ai4dev.tinderfordogs.ai.common.service.LiteLLMService
import com.ai4dev.tinderfordogs.ai.common.service.Message
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import kotlin.collections.plus

/**
 * Handles multi-turn support conversations by delegating to a configurable LLM via [LiteLLMService].
 *
 * Appends the incoming user message to the provided conversation history before sending the full
 * context to the model, ensuring coherent multi-turn dialogue. The model used is resolved at
 * startup from the `tinder4dogs.support.model` configuration property, allowing the support
 * assistant to be swapped for a fine-tuned or alternative model without code changes.
 */
@Service
class SupportAssistantService(
    private val llm: LiteLLMService,
    @Value($$"${tinder4dogs.support.model}") private val model: String,
) {
    suspend fun answer(
        userMessage: String,
        conversationHistory: List<Message> = emptyList(),
    ): String {
        val messages = conversationHistory + Message("user", userMessage)

        return llm
            .chat(ChatRequest(model, messages))
            .choices
            .first()
            .message.content
    }
}
