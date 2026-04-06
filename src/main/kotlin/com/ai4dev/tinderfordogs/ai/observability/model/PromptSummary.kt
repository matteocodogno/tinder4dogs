package com.ai4dev.tinderfordogs.ai.observability.model

data class PromptSummary(
    val id: String,
    val version: String,
    val tags: List<String>,
    val label: String,
)
