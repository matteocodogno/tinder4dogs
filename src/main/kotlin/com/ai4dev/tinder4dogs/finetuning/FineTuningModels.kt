package com.ai4dev.tinder4dogs.finetuning

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

/** What the pipeline did, step by step. The endpoint returns this. */
data class PipelineReport(
    val loaded: Int,
    val firstClean: CleaningReport,
    val augmented: Int,
    val finalClean: CleaningReport,
    val train: Int,
    val validation: Int,
    val test: Int,
    val files: Map<String, String>,
)
