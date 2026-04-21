package com.ai4dev.tinderfordogs.dogprofile.presentation

import com.ai4dev.tinderfordogs.common.model.ErrorResponse
import com.ai4dev.tinderfordogs.dogprofile.model.CreateDogProfileRequest
import com.ai4dev.tinderfordogs.dogprofile.model.DogMatchResponse
import com.ai4dev.tinderfordogs.dogprofile.model.DogProfileResponse
import com.ai4dev.tinderfordogs.dogprofile.service.DogProfileService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/dogs")
class DogProfileController(
    private val service: DogProfileService,
) {
    @GetMapping
    fun findAll(): List<DogProfileResponse> = service.findAll()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: CreateDogProfileRequest,
    ): DogProfileResponse = service.create(request)

    @GetMapping("/{id}/matches")
    fun findMatches(
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "10") limit: Int,
    ): List<DogMatchResponse> = service.findMatches(id, limit)

    @ExceptionHandler(NoSuchElementException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleNotFound(ex: NoSuchElementException): ErrorResponse =
        ErrorResponse(code = "NOT_FOUND", message = ex.message ?: "Not found")

    @ExceptionHandler(MethodArgumentNotValidException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleValidation(ex: MethodArgumentNotValidException): ErrorResponse {
        val message =
            ex.bindingResult.fieldErrors
                .firstOrNull()
                ?.let { "${it.field}: ${it.defaultMessage}" }
                ?: ex.message ?: "Validation failed"
        return ErrorResponse(code = "VALIDATION_ERROR", message = message)
    }
}
