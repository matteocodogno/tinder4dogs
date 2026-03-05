package com.ai4dev.tinderfordogs.ai.common.service

import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.service.annotation.HttpExchange
import org.springframework.web.service.annotation.PostExchange

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

@HttpExchange
interface LiteLLMService {
    @PostExchange("/chat/completions")
    fun chat(
        @RequestBody request: ChatRequest,
    ): ChatResponse

    @PostExchange("/embeddings")
    fun embed(
        @RequestBody request: EmbedRequest,
    ): EmbedResponse
}
