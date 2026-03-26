package com.ai4dev.tinderfordogs.ai.finetuning.service

import com.ai4dev.tinderfordogs.ai.finetuning.model.ChatMessage
import com.ai4dev.tinderfordogs.ai.finetuning.model.SeedExample
import com.ai4dev.tinderfordogs.ai.finetuning.model.TrainingExample
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue

@Component
class RawDataLoader(
    private val mapper: ObjectMapper,
) {
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

    fun loadSeedExamples(path: String = "fine-tuning/seed-examples.json"): List<SeedExample> {
        val resource = ClassPathResource(path)
        return mapper.readValue(resource.inputStream)
    }

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
