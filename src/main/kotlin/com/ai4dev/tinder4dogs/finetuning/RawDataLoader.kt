package com.ai4dev.tinder4dogs.finetuning

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

/** Step 1. Reads the hand-written seed examples and wraps each in a chat. */
@Component
class RawDataLoader {
    // Spring Boot 4 publishes no Jackson 2 ObjectMapper bean, so this one is
    // local. Same reason as LangfusePromptSource.
    private val mapper = jacksonObjectMapper()

    companion object {
        val SYSTEM_PROMPT =
            """
            You are the Tinder for Dogs support assistant.
            Reply in English with a professional, friendly, and concise tone.
            For app-related questions, always cite the relevant documentation section.
            For out-of-scope questions (medical, legal, unrelated topics), politely
            decline and redirect to the appropriate professional.
            Never make up information you don't have in your context.
            """.trimIndent()
    }

    fun loadSeedExamples(path: String = "fine-tuning/seed-examples.json"): List<SeedExample> =
        ClassPathResource(path).inputStream.use { mapper.readValue(it) }

    fun toTrainingExamples(
        seeds: List<SeedExample>,
        systemPrompt: String,
    ): List<TrainingExample> =
        seeds.map { seed ->
            TrainingExample(
                messages =
                    listOf(
                        ChatMessage("system", systemPrompt),
                        ChatMessage("user", seed.user),
                        ChatMessage("assistant", seed.assistant),
                    ),
                intent = seed.intent,
                source = "seed",
            )
        }
}
