package com.ai4dev.tinderfordogs.support.service

import com.ai4dev.tinderfordogs.ai.common.model.ChatRequest
import com.ai4dev.tinderfordogs.ai.common.model.Message
import com.ai4dev.tinderfordogs.ai.common.service.LiteLLMService
import com.ai4dev.tinderfordogs.support.model.ModelComparison
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class SupportEvaluationService(
    private val llm: LiteLLMService,
) {
    companion object {
        private const val FINE_TUNED = "support-assistant"
        private const val BASE_MODEL = "gpt-4o-mini" // add this alias to litellm.yaml
    }

    suspend fun compare(question: String): ModelComparison {
        logger.info { "Comparing models for question: $question" }
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
