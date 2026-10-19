package com.ai4dev.tinder4dogs.finetuning

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component

/**
 * Step 3. Asks a model for paraphrases of each user question and keeps the
 * same answer. Eight seeds become eighty-eight examples.
 *
 * The model is the application's default alias (`AI4DEV_MODEL`, `cheap` in
 * the course), through the gateway like every other call.
 */
@Component
class DataAugmentor(
    private val chatClients: ObjectProvider<ChatClient.Builder>,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val mapper = jacksonObjectMapper()

    /**
     * For each example, generates [variantsPerExample] paraphrased user questions
     * while keeping the same system prompt and assistant answer.
     */
    fun augment(
        examples: List<TrainingExample>,
        variantsPerExample: Int = 3,
    ): List<TrainingExample> =
        examples.flatMap { original ->
            val variants = generateVariants(original, variantsPerExample)
            listOf(original) + variants
        }

    private fun generateVariants(
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
            val builder = chatClients.ifAvailable ?: error("no chat model configured")
            val raw = builder.build().prompt().user(prompt).call().content().orEmpty()
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
            log.warn("⚠️ Augmentation failed for intent '{}': {}", example.intent, e.message)
            emptyList()
        }
    }
}
