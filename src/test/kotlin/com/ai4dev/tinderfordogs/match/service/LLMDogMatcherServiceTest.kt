package com.ai4dev.tinderfordogs.match.service

import com.ai4dev.tinderfordogs.ai.common.model.ChatRequest
import com.ai4dev.tinderfordogs.ai.common.model.ChatResponse
import com.ai4dev.tinderfordogs.ai.common.model.Choice
import com.ai4dev.tinderfordogs.ai.common.model.Message
import com.ai4dev.tinderfordogs.ai.common.service.LiteLLMService
import com.ai4dev.tinderfordogs.ai.observability.model.OutputFormat
import com.ai4dev.tinderfordogs.ai.observability.model.PromptRole
import com.ai4dev.tinderfordogs.ai.observability.model.PromptTemplate
import com.ai4dev.tinderfordogs.ai.observability.service.PromptRegistry
import com.ai4dev.tinderfordogs.dogprofile.model.DogGender
import com.ai4dev.tinderfordogs.dogprofile.model.DogProfile
import com.ai4dev.tinderfordogs.dogprofile.model.DogSize
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class LLMDogMatcherServiceTest {
    private val liteLLMService = mockk<LiteLLMService>()
    private val promptRegistry = mockk<PromptRegistry>()
    private lateinit var llmDogMatcherService: LLMDogMatcherService

    private val dog1 = DogProfile(UUID.randomUUID(), "Buddy", "Golden Retriever", DogSize.LARGE, 3, DogGender.MALE)
    private val dog2 = DogProfile(UUID.randomUUID(), "Bella", "Labrador", DogSize.LARGE, 2, DogGender.FEMALE)

    private val template = PromptTemplate(
        id = "dog-matcher",
        version = "1",
        role = PromptRole.DEVELOPER,
        systemPrompt = "System Prompt",
        userTemplate = "Dog 1: {{dog1_name}}, Dog 2: {{dog2_name}}",
        outputFormat = OutputFormat.JSON,
        model = "gpt-4o",
        maxTokens = 500,
        temperature = 0.2,
    )

    @BeforeEach
    fun setUp() {
        llmDogMatcherService = LLMDogMatcherService(liteLLMService, promptRegistry)
        every { promptRegistry.getBlocking("dog-matcher") } returns template
    }

    private fun chatResponseWith(content: String) = ChatResponse(
        choices = listOf(Choice(message = Message(role = "assistant", content = content))),
    )

    @Test
    fun `should return LLM compatibility result on successful chat completion`() {
        val json = """{"score": 85, "reasoning": "Both dogs love playing fetch and have similar energy levels."}"""
        every { liteLLMService.chat(any<ChatRequest>()) } returns chatResponseWith(json)

        val result = llmDogMatcherService.calculateCompatibility(dog1, dog2)

        assertEquals(85, result.score)
        assertEquals("Both dogs love playing fetch and have similar energy levels.", result.reasoning)
    }

    @Test
    fun `should parse JSON wrapped in json markdown block`() {
        val json = """{"score": 72, "reasoning": "Good match."}"""
        every { liteLLMService.chat(any<ChatRequest>()) } returns chatResponseWith("```json\n$json\n```")

        val result = llmDogMatcherService.calculateCompatibility(dog1, dog2)

        assertEquals(72, result.score)
        assertEquals("Good match.", result.reasoning)
    }

    @Test
    fun `should parse JSON wrapped in plain markdown block`() {
        val json = """{"score": 60, "reasoning": "Decent match."}"""
        every { liteLLMService.chat(any<ChatRequest>()) } returns chatResponseWith("```\n$json\n```")

        val result = llmDogMatcherService.calculateCompatibility(dog1, dog2)

        assertEquals(60, result.score)
        assertEquals("Decent match.", result.reasoning)
    }

    @Test
    fun `should fallback to heuristic matcher when LiteLLMService throws exception`() {
        val dog1Fallback = DogProfile(UUID.randomUUID(), "Max", "Poodle", DogSize.MEDIUM, 3, DogGender.MALE)
        val dog2Fallback = DogProfile(UUID.randomUUID(), "Lucy", "Poodle", DogSize.MEDIUM, 2, DogGender.FEMALE)
        every { liteLLMService.chat(any<ChatRequest>()) } throws RuntimeException("AI service down")

        val result = llmDogMatcherService.calculateCompatibility(dog1Fallback, dog2Fallback)

        // same breed→25, age diff 1→30, diff gender→15 = 70/100 → score 70
        assertEquals(70, result.score)
        assertTrue(result.reasoning.startsWith("Fallback"))
    }
}
