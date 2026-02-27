package com.ai4dev.tinderfordogs.support.service

import com.ai4dev.tinderfordogs.ai.common.service.ChatRequest
import com.ai4dev.tinderfordogs.ai.common.service.LiteLLMService
import com.ai4dev.tinderfordogs.ai.common.service.Message
import org.springframework.stereotype.Service

data class ModelComparison(
    val question: String,
    val fineTunedAnswer: String,
    val baseModelAnswer: String,
)

@Service
class SupportEvaluationService(
    private val llm: LiteLLMService,
) {
    companion object {
        private const val FINE_TUNED = "support-assistant"
        private const val BASE_MODEL = "gpt-4o-mini" // add this alias to litellm.yaml
    }

    suspend fun compare(question: String): ModelComparison {
        val messages = listOf(Message("user", question))

        return ModelComparison(
            question = question,
            fineTunedAnswer =
                llm
                    .chat(ChatRequest(FINE_TUNED, messages))
                    .choices
                    .first()
                    .message.content,
            baseModelAnswer =
                llm
                    .chat(ChatRequest(BASE_MODEL, messages))
                    .choices
                    .first()
                    .message.content,
        )
    }
}
