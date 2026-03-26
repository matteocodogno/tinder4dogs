package com.ai4dev.tinderfordogs.ai.common.service

import com.ai4dev.tinderfordogs.ai.common.model.ChatRequest
import com.ai4dev.tinderfordogs.ai.common.model.ChatResponse
import com.ai4dev.tinderfordogs.ai.common.model.EmbedRequest
import com.ai4dev.tinderfordogs.ai.common.model.EmbedResponse
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.service.annotation.HttpExchange
import org.springframework.web.service.annotation.PostExchange

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
