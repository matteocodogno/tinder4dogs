package com.ai4dev.tinderfordogs.support.presentation

import com.ai4dev.tinderfordogs.ai.common.service.Message
import com.ai4dev.tinderfordogs.support.service.SupportAssistantService
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

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
    private val support: SupportAssistantService,
    @Value($$"${tinder4dogs.support.model}")
    private val model: String,
) {
    @PostMapping("/ask")
    suspend fun ask(
        @RequestBody req: SupportRequest,
    ): SupportResponse =
        SupportResponse(
            answer = support.answer(req.message, req.history),
            model = model,
        )
}
