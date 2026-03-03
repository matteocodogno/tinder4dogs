package com.ai4dev.tinderfordogs.ai.observability.model

import com.ai4dev.tinderfordogs.ai.observability.service.LangfusePrompt
import java.time.Instant

data class CachedPrompt(
    val prompt: LangfusePrompt,
    val cachedAt: Instant,
)
