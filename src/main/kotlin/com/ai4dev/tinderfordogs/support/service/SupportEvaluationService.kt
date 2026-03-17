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

/**
 * Evaluates the quality gap between the fine-tuned support assistant and the baseline model
 * by running the same question through both and surfacing their responses side by side.
 *
 * Sends an identical user message concurrently to the `support-assistant` fine-tuned model
 * and the `gpt-4o-mini` base model via [LiteLLMService], allowing developers and product
 * owners to qualitatively assess whether fine-tuning has produced measurably better support
 * answers. Neither result is persisted — this service is intended for ad-hoc evaluation
 * and A/B comparison workflows, not production support traffic.
 */
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
