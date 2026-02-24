package com.ai4dev.tinderfordogs.support

import com.ai4dev.tinderfordogs.ai.finetuning.service.ChatRequest
import com.ai4dev.tinderfordogs.ai.finetuning.service.LiteLLMService
import com.ai4dev.tinderfordogs.ai.finetuning.service.Message
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class SupportAssistantService(
    private val llm: LiteLLMService,
    @Value($$"${tinder4dogs.support.model}") private val model: String
) {

    suspend fun answer(userMessage: String, conversationHistory: List<Message> = emptyList()): String {
        val messages = conversationHistory + Message("user", userMessage)

        return llm.chat(ChatRequest(model, messages)).choices.first().message.content
    }
}
