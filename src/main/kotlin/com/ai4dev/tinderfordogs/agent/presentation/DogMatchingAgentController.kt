package com.ai4dev.tinderfordogs.agent.presentation

import com.ai4dev.tinderfordogs.agent.model.AgentResponse
import com.ai4dev.tinderfordogs.agent.model.ChatRequest
import com.ai4dev.tinderfordogs.agent.service.DogMatchingAgentService
import com.ai4dev.tinderfordogs.common.model.ErrorResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Dog Matching Agent", description = "AI agent that finds the best companion dog via natural language")
@RestController
@RequestMapping("/api/v1/agent")
class DogMatchingAgentController(
    private val service: DogMatchingAgentService,
) {
    @Operation(
        summary = "Find the best dog match",
        description = "Describe the reference dog, location, and preferred date in plain text. " +
            "The agent resolves the location, searches nearby dogs, scores compatibility, " +
            "and checks owner availability before returning its recommendation.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Recommendation returned"),
        ApiResponse(
            responseCode = "400", description = "Invalid request",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @PostMapping("/chat")
    fun chat(
        @Valid @RequestBody request: ChatRequest,
    ): AgentResponse = service.chat(request.message)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleValidation(ex: MethodArgumentNotValidException): ErrorResponse {
        val message = ex.bindingResult.fieldErrors.joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
        return ErrorResponse("VALIDATION_ERROR", message)
    }

    @ExceptionHandler(ConstraintViolationException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleConstraint(ex: ConstraintViolationException): ErrorResponse =
        ErrorResponse("VALIDATION_ERROR", ex.message ?: "constraint violation")
}
