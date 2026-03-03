package com.ai4dev.tinderfordogs.ai.observability.model

enum class PromptRole {
    DEVELOPER,
    SECURITY_ENGINEER,
    QA,
    ARCHITECT,
}

enum class OutputFormat {
    JSON,
    MARKDOWN,
    PLAIN,
    KOTLIN_CODE,
}

data class PromptTemplate(
    val id: String,
    val version: String,
    val role: PromptRole,
    val systemPrompt: String,
    val userTemplate: String,
    val outputFormat: OutputFormat,
    val model: String,
    val maxTokens: Int,
    val temperature: Double,
    val topP: Double = 0.9,
    val stopSequences: List<String> = emptyList(),
    val frequencyPenalty: Double = 0.0,
    val presencePenalty: Double = 0.0,
    val tags: List<String> = emptyList(),
    val changelog: String = "",
)

data class PromptResult(
    val output: String,
    val traceId: String,
    val latencyMs: Long,
    val promptId: String,
    val promptVersion: String,
)

data class SanitizationResult(
    val sanitizedInput: String = "",
    val blocked: Boolean,
    val reason: String = "",
)

data class PromptConfig(
    val temperature: Double,
    val topP: Double,
    val maxTokens: Int,
    val frequencyPenalty: Double = 0.0,
    val presencePenalty: Double = 0.0,
)

object PromptPresets {
    val STRUCTURED = PromptConfig(temperature = 0.1, topP = 0.9, maxTokens = 2000)
    val ANALYTICAL = PromptConfig(temperature = 0.3, topP = 0.9, maxTokens = 3000)
    val CREATIVE =
        PromptConfig(
            temperature = 0.8,
            topP = 0.95,
            maxTokens = 1500,
            frequencyPenalty = 0.5,
            presencePenalty = 0.3,
        )
    val FACTUAL = PromptConfig(temperature = 0.0, topP = 0.1, maxTokens = 1000)
}
