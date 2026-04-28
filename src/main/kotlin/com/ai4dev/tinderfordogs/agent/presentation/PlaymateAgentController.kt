package com.ai4dev.tinderfordogs.agent.presentation

import com.ai4dev.tinderfordogs.agent.model.PlaymateRequest
import com.ai4dev.tinderfordogs.agent.model.PlaymateResponse
import com.ai4dev.tinderfordogs.agent.service.PlaymateAgentService
import com.ai4dev.tinderfordogs.common.model.ErrorResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/agent")
class PlaymateAgentController(
    private val service: PlaymateAgentService,
) {
    @PostMapping("/find-playmate")
    fun findPlaymate(
        @RequestBody @Valid request: PlaymateRequest,
    ): PlaymateResponse = PlaymateResponse(recommendation = service.findPlaymate(request.prompt))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleValidation(ex: MethodArgumentNotValidException): ErrorResponse {
        val message =
            ex.bindingResult.fieldErrors
                .firstOrNull()
                ?.let { "${it.field}: ${it.defaultMessage}" }
                ?: "Validation failed"
        return ErrorResponse(code = "VALIDATION_ERROR", message = message)
    }
}
