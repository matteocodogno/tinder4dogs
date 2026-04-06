package com.ai4dev.tinderfordogs.ai.observability.model

import java.util.UUID

data class PromptExecuteRequest(
    val promptId: String,
    val variables: Map<String, String>,
    val sessionId: String = UUID.randomUUID().toString(),
    val label: String? = null, // null → falls back to environment default
)
