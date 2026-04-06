package com.ai4dev.tinderfordogs.ai.observability.model

import java.time.Instant

data class CachedPrompt(
    val prompt: LangfusePrompt,
    val cachedAt: Instant,
)
