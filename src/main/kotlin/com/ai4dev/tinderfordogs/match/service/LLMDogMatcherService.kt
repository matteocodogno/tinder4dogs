package com.ai4dev.tinderfordogs.match.service

import com.ai4dev.tinderfordogs.ai.common.model.ChatRequest
import com.ai4dev.tinderfordogs.ai.common.model.Message
import com.ai4dev.tinderfordogs.ai.common.service.LiteLLMService
import com.ai4dev.tinderfordogs.ai.observability.service.PromptRegistry
import com.ai4dev.tinderfordogs.dogprofile.model.DogProfile
import com.ai4dev.tinderfordogs.match.model.LLMCompatibilityResult
import com.ai4dev.tinderfordogs.match.service.CompatibilityScorer
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class LLMDogMatcherService(
    private val liteLLMService: LiteLLMService,
    private val promptRegistry: PromptRegistry,
) {
    private val objectMapper = jacksonObjectMapper()

    fun calculateCompatibility(
        dog1: DogProfile,
        dog2: DogProfile,
    ): LLMCompatibilityResult =
        try {
            val template = promptRegistry.getBlocking("dog-matcher")

            val userPrompt =
                template.userTemplate
                    .replace("{{dog1_name}}", dog1.name)
                    .replace("{{dog1_breed}}", dog1.breed)
                    .replace("{{dog1_age}}", dog1.age.toString())
                    .replace("{{dog1_gender}}", dog1.gender.toString())
                    .replace("{{dog2_name}}", dog2.name)
                    .replace("{{dog2_breed}}", dog2.breed)
                    .replace("{{dog2_age}}", dog2.age.toString())
                    .replace("{{dog2_gender}}", dog2.gender.toString())

            val request =
                ChatRequest(
                    model = template.model,
                    messages =
                        listOf(
                            Message(role = "system", content = template.systemPrompt),
                            Message(role = "user", content = userPrompt),
                        ),
                )

            val response = liteLLMService.chat(request)
            val content =
                response.choices
                    .first()
                    .message.content

            // Try to extr act JSON if it's wrapped in markdown blocks
            val jsonContent =
                if (content.startsWith("```json")) {
                    content.substringAfter("```json").substringBeforeLast("```").trim()
                } else if (content.startsWith("```")) {
                    content.substringAfter("```").substringBeforeLast("```").trim()
                } else {
                    content.trim()
                }

            objectMapper.readValue<LLMCompatibilityResult>(jsonContent)
        } catch (e: Exception) {
            logger.warn {
                "LLM matching failed for dogs ${dog1.id} and ${dog2.id}. Falling back to heuristic matcher. Error: " +
                    "${e.message}"
            }
            val fallbackScoreDouble = CompatibilityScorer.score(dog1, dog2)
            val fallbackScoreInt = (fallbackScoreDouble * 100).toInt()
            LLMCompatibilityResult(
                score = fallbackScoreInt,
                reasoning = "Fallback: Used heuristic matcher due to AI service unavailability.",
            )
        }
}
