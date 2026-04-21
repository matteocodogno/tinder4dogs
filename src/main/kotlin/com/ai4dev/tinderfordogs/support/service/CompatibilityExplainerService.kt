package com.ai4dev.tinderfordogs.support.service

import com.ai4dev.tinderfordogs.ai.common.model.ChatRequest
import com.ai4dev.tinderfordogs.ai.common.model.Message
import com.ai4dev.tinderfordogs.ai.common.service.LiteLLMService
import com.ai4dev.tinderfordogs.ai.observability.service.LangfuseService
import com.ai4dev.tinderfordogs.ai.observability.service.PromptRegistry
import com.ai4dev.tinderfordogs.ai.observability.service.renderTemplate
import com.ai4dev.tinderfordogs.dogprofile.model.DogProfile
import org.springframework.stereotype.Service

@Service
class CompatibilityExplainerService(
    private val liteLlmClient: LiteLLMService,
    private val promptRegistry: PromptRegistry,
    private val langfuse: LangfuseService,
) {
    suspend fun explain(dogA: DogProfile, dogB: DogProfile, score: Int): String {
        val template = promptRegistry.get("compatibility-explainer", "production")

        val userMessage = template.renderTemplate(
            mapOf(
                "dogA.name" to dogA.name,
                "dogA.breed" to dogA.breed,
                "dogA.age" to dogA.age.toString(),
                "dogA.energyLevel" to "7", // TODO: retrieve from breed characteristics
                "dogA.friendlinessScore" to "8", // TODO: retrieve from breed characteristics
                "dogB.name" to dogB.name,
                "dogB.breed" to dogB.breed,
                "dogB.age" to dogB.age.toString(),
                "dogB.energyLevel" to "6", // TODO: retrieve from breed characteristics
                "dogB.friendlinessScore" to "9", // TODO: retrieve from breed characteristics
                "score" to score.toString(),
            ),
        )

        val traceId = langfuse.startTrace(
            name = "compatibility-explanation",
            input = mapOf("dogA" to dogA.name, "dogB" to dogB.name, "score" to score),
        )

        val chatRequest = ChatRequest(
            model = template.model,
            messages = listOf(
                Message("system", template.systemPrompt),
                Message("user", userMessage),
            ),
            metadata = mapOf("trace_id" to traceId),
        )

        val response = liteLlmClient.chat(chatRequest)

        return response.choices.first().message.content
    }
}