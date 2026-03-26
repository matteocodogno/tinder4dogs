package com.ai4dev.tinderfordogs.ai.finetuning.model

data class SeedExample(
    val intent: String,
    val user: String,
    val assistant: String,
)

data class ChatMessage(
    val role: String,
    val content: String,
)

data class TrainingExample(
    val messages: List<ChatMessage>,
    val intent: String,
    val source: String, // "seed" | "augmented"
)

data class DatasetSplit(
    val train: List<TrainingExample>,
    val validation: List<TrainingExample>,
    val test: List<TrainingExample>,
) {
    val total get() = train.size + validation.size + test.size
}

data class CleaningReport(
    val inputCount: Int,
    val removedDuplicates: Int,
    val removedTooShort: Int,
    val removedTooLong: Int,
    val removedInvalidFormat: Int,
    val piiRedacted: Int,
    val outputCount: Int,
)
