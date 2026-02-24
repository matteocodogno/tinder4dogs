package com.ai4dev.tinderfordogs.support.presentation

import com.ai4dev.tinderfordogs.ai.finetuning.service.Message
import com.ai4dev.tinderfordogs.support.SupportAssistantService
import org.springframework.beans.factory.annotation.Value

import org.springframework.web.bind.annotation.*

data class SupportRequest(
    val message: String,
    val history: List<Message> = emptyList(),
)

data class SupportResponse(
    val answer: String,
    val model: String,
)

@RestController
@RequestMapping("/api/v1/support")
class SupportController(
    private val service: SupportAssistantService,
    @Value($$"${tinder4dogs.support.model}")
    private val model: String,
) {
    @PostMapping("/ask")
    suspend fun ask(
        @RequestBody req: SupportRequest,
    ): SupportResponse =
        SupportResponse(
            answer = service.answer(req.message, req.history),
            model = model,
        )
}
