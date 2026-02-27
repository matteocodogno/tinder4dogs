package com.ai4dev.tinderfordogs.ai.finetuning.service

import com.ai4dev.tinderfordogs.ai.common.service.ChatRequest
import com.ai4dev.tinderfordogs.ai.common.service.LiteLLMService
import com.ai4dev.tinderfordogs.ai.common.service.Message
import com.ai4dev.tinderfordogs.ai.finetuning.model.ChatMessage
import com.ai4dev.tinderfordogs.ai.finetuning.model.TrainingExample
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import kotlin.collections.plus

@Component
class DataAugmentor(
    private val llm: LiteLLMService,
    private val mapper: ObjectMapper,
) {
    companion object {
        private const val AUGMENT_MODEL = "local-fast" // Claude / GPT-4o via LiteLLM
    }

    /**
     * For each example, generates [variantsPerExample] paraphrased user questions
     * while keeping the same system prompt and assistant answer.
     */
    suspend fun augment(
        examples: List<TrainingExample>,
        variantsPerExample: Int = 3,
    ): List<TrainingExample> =
        examples.flatMap { original ->
            val variants = generateVariants(original, variantsPerExample)
            listOf(original) + variants
        }

    private suspend fun generateVariants(
        example: TrainingExample,
        count: Int,
    ): List<TrainingExample> {
        val userMessage = example.messages.first { it.role == "user" }.content
        val assistantMessage = example.messages.first { it.role == "assistant" }.content
        val systemMessage = example.messages.first { it.role == "system" }.content

        val prompt =
            """
            You are a data augmentation assistant for a dog-matching app support system.

            Original question: "$userMessage"
            
            Generate exactly $count alternative ways a user might ask the same question,
            covering different tones: neutral, informal, frustrated.
            Respond ONLY with a JSON array of strings. Example:
            ["alternative 1", "alternative 2", "alternative 3"]
            Do not wrap response in backticks, return only the JSON array.
            """.trimIndent()

        return try {
            val raw =
                llm
                    .chat(ChatRequest(AUGMENT_MODEL, listOf(Message("user", prompt))))
                    .choices
                    .first()
                    .message.content
            val variants = mapper.readValue<List<String>>(raw.trim())
            variants.map { variantQuestion ->
                example.copy(
                    messages =
                        listOf(
                            ChatMessage("system", systemMessage),
                            ChatMessage("user", variantQuestion),
                            ChatMessage("assistant", assistantMessage),
                        ),
                    source = "augmented",
                )
            }
        } catch (e: Exception) {
            // If parsing fails, skip augmentation for this example
            println("⚠️ Augmentation failed for intent '${example.intent}': ${e.message}")
            emptyList()
        }
    }
}
