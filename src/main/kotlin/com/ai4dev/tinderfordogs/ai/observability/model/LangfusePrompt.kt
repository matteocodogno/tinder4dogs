package com.ai4dev.tinderfordogs.ai.observability.model

data class LangfusePrompt(
    val name: String,
    val version: Int,
    val type: String,
    val prompt: Any,
    val config: Map<String, Any> = emptyMap(),
    val labels: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
)
