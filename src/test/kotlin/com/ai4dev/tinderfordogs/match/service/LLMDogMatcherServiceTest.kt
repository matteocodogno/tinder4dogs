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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class LLMDogMatcherServiceTest {
    @Mock
    private lateinit var liteLLMService: LiteLLMService

    @Mock
    private lateinit var promptRegistry: PromptRegistry

    private lateinit var llmDogMatcherService: LLMDogMatcherService

    @BeforeEach
    fun setUp() {
        llmDogMatcherService =
            LLMDogMatcherService(
                liteLLMService,
                promptRegistry,
            )
    }

    private fun anyChatRequest(): ChatRequest {
        Mockito.any<ChatRequest>()
        return ChatRequest("dummy", emptyList())
    }

    private fun eqString(value: String): String {
        Mockito.eq(value)
        return value
    }

    private fun anyString(): String {
        Mockito.anyString()
        return ""
    }

    @Test
    fun `should return LLM compatibility result on successful chat completion`() {
        val dog1 = DogProfile(UUID.randomUUID(), "Buddy", "Golden Retriever", DogSize.LARGE, 3, DogGender.MALE)
        val dog2 = DogProfile(UUID.randomUUID(), "Bella", "Labrador", DogSize.LARGE, 2, DogGender.FEMALE)

        val template =
            PromptTemplate(
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

        `when`(promptRegistry.getBlocking(eqString("dog-matcher"))).thenReturn(template)

        val jsonResponse = """{"score": 85, "reasoning": "Both dogs love playing fetch and have similar energy levels."}"""
        val chatResponse =
            ChatResponse(
                choices = listOf(Choice(message = Message(role = "assistant", content = jsonResponse))),
            )

        `when`(liteLLMService.chat(anyChatRequest())).thenReturn(chatResponse)

        val result = llmDogMatcherService.calculateCompatibility(dog1, dog2)

        assertEquals(85, result.score)
        assertEquals("Both dogs love playing fetch and have similar energy levels.", result.reasoning)
    }

    @Test
    fun `should fallback to heuristic matcher when LiteLLMService throws exception`() {
        val dog1 = DogProfile(UUID.randomUUID(), "Max", "Poodle", DogSize.MEDIUM, 3, DogGender.MALE)
        val dog2 = DogProfile(UUID.randomUUID(), "Lucy", "Poodle", DogSize.MEDIUM, 2, DogGender.FEMALE)

        val template =
            PromptTemplate(
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

        `when`(promptRegistry.getBlocking(eqString("dog-matcher"))).thenReturn(template)
        `when`(liteLLMService.chat(anyChatRequest())).thenThrow(RuntimeException("AI service down"))

        val result = llmDogMatcherService.calculateCompatibility(dog1, dog2)

        assertEquals(70, result.score)
        assertTrue(result.reasoning.startsWith("Fallback"))
    }
}
