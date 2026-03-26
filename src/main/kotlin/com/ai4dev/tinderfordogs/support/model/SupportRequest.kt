package com.ai4dev.tinderfordogs.support.model

import com.ai4dev.tinderfordogs.ai.common.model.Message

data class SupportRequest(
    val message: String,
    val history: List<Message> = emptyList(),
)
