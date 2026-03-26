package com.ai4dev.tinderfordogs.ai.common.model

data class Message(
    val role: String,
    val content: String,
)

data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val metadata: Map<String, String>? = null,
)

data class EmbedRequest(
    val model: String,
    val input: String,
)

data class ChatResponse(
    val choices: List<Choice>,
)

data class Choice(
    val message: Message,
)

data class EmbedResponse(
    val data: List<EmbedData>,
)

data class EmbedData(
    val embedding: List<Double>,
)
