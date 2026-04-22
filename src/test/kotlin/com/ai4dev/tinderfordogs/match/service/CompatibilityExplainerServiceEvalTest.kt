package com.ai4dev.tinderfordogs.match.service

import com.ai4dev.tinderfordogs.ai.common.model.ChatRequest
import com.ai4dev.tinderfordogs.ai.common.model.Message
import com.ai4dev.tinderfordogs.ai.common.service.LiteLLMService
import com.ai4dev.tinderfordogs.ai.observability.service.LangfuseService
import com.ai4dev.tinderfordogs.ai.observability.service.PromptRegistry
import com.ai4dev.tinderfordogs.dogprofile.model.DogGender
import com.ai4dev.tinderfordogs.dogprofile.model.DogProfile
import com.ai4dev.tinderfordogs.dogprofile.model.DogSize
import com.ai4dev.tinderfordogs.support.service.CompatibilityExplainerService
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.web.client.RestClient
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory
import java.util.*

class CompatibilityExplainerServiceEvalTest {
    private lateinit var explainerService: CompatibilityExplainerService
    private lateinit var liteLLMService: LiteLLMService
    private val objectMapper = jacksonObjectMapper()

    private val rubricPath = "prompts/evals/compatibility-explainer-rubric.md"

    data class EvaluationResult(
        val tone_calibration: EvaluationDetail,
        val constraint_adherence: EvaluationDetail,
        val factuality: EvaluationDetail,
        val overall_decision: String,
    )

    data class EvaluationDetail(
        val status: String,
        val reasoning: String,
    )

    @BeforeEach
    fun setUp() {
        // Use environment variables for LiteLLM config
        val baseUrl = System.getenv("LITELLM_URL") ?: "http://localhost:4000"
        val apiKey = System.getenv("LITELLM_KEY") ?: "test-key"

        val restClient =
            RestClient
                .builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer $apiKey")
                .build()
        val adapter = RestClientAdapter.create(restClient)
        val factory = HttpServiceProxyFactory.builderFor(adapter).build()
        liteLLMService = factory.createClient(LiteLLMService::class.java)

        // Mock dependencies that we don't need for the core logic or that are hard to init
        val promptRegistry = mockk<PromptRegistry>()
        val langfuseService = mockk<LangfuseService>()

        // Mock PromptRegistry to return the real prompt template
        val promptContent = ClassPathResource("prompts/compatibility-explainer.yaml").inputStream.bufferedReader().use { it.readText() }
        // Note: In a real scenario, you'd parse the YAML. For this test, let's assume the service works.
        // Actually, let's just mock the explainer service's explain method if we want to test JUST the judge,
        // OR we use a real Registry if it's easy.
        // For simplicity, let's use a real CompatibilityExplainerService but mock its dependencies.

        // We need to mock promptRegistry.get(...)
        // But promptRegistry is a class, not an interface.
        // Let's see if we can use the real one. It seems it just needs some YAMLs.

        explainerService = CompatibilityExplainerService(liteLLMService, promptRegistry, langfuseService)

        every { langfuseService.startTrace(any(), any(), any(), any()) } returns "test-trace-id"
    }

    private fun runEvaluation(
        dogA: DogProfile,
        dogB: DogProfile,
        score: Int,
        output: String,
    ): EvaluationResult {
        val rubric = ClassPathResource(rubricPath).inputStream.bufferedReader().use { it.readText() }

        val dogAInfo = "Name: ${dogA.name}, Breed: ${dogA.breed}, Age: ${dogA.age}"
        val dogBInfo = "Name: ${dogB.name}, Breed: ${dogB.breed}, Age: ${dogB.age}"

        val systemPrompt =
            rubric
                .replace("{dogA}", dogAInfo)
                .replace("{dogB}", dogBInfo)
                .replace("{score}", score.toString())
                .replace("{output}", output)

        val request =
            ChatRequest(
                model = "gpt-4o",
                messages =
                    listOf(
                        Message(role = "system", content = systemPrompt),
                        Message(role = "user", content = "Evaluate the assistant output and return the JSON evaluation."),
                    ),
            )

        val response = liteLLMService.chat(request)
        val content =
            response.choices
                .first()
                .message.content

        val jsonString = content.substringAfter("```json").substringBefore("```").trim()

        return objectMapper.readValue(jsonString)
    }

    @Test
    fun `test judge fails a false positive output`() {
        val dogA = DogProfile(UUID.randomUUID(), "Bella", "Poodle", DogSize.MEDIUM, 2, DogGender.FEMALE)
        val dogB = DogProfile(UUID.randomUUID(), "Max", "Bulldog", DogSize.MEDIUM, 5, DogGender.MALE)
        val score = 23

        val falsePositiveExplanation =
            "Bella and Max are a wonderful match! " +
                "Their energy levels are perfectly compatible and they will have so much fun together."

        val evaluation = runEvaluation(dogA, dogB, score, falsePositiveExplanation)

        assertEquals("FAIL_FALSE_POSITIVE", evaluation.tone_calibration.status)
        assertEquals("FAIL", evaluation.overall_decision)
    }

    // Add more tests if needed, but this one verifies the rubric works.
}
