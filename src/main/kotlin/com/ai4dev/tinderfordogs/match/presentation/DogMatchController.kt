package com.ai4dev.tinderfordogs.match.presentation

import com.ai4dev.tinderfordogs.common.model.ErrorResponse
import com.ai4dev.tinderfordogs.match.model.DogMatchListResponse
import com.ai4dev.tinderfordogs.match.model.DogNotFoundException
import com.ai4dev.tinderfordogs.match.service.DogMatchService
import jakarta.validation.ConstraintViolationException
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import java.util.UUID

@RestController
@RequestMapping("/api/v1/dogs")
@Validated
class DogMatchController(
    private val service: DogMatchService,
) {
    @GetMapping("/{dogId}/matches")
    fun findMatches(
        @PathVariable dogId: UUID,
        @RequestParam(defaultValue = "1") @Min(1) @Max(10) limit: Int,
    ): DogMatchListResponse = service.findMatches(dogId, limit)

    @ExceptionHandler(DogNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleNotFound(ex: DogNotFoundException): ErrorResponse =
        ErrorResponse(code = "DOG_NOT_FOUND", message = ex.message ?: "Dog not found")

    @ExceptionHandler(ConstraintViolationException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleConstraintViolation(ex: ConstraintViolationException): ErrorResponse {
        val message =
            ex.constraintViolations
                .firstOrNull()
                ?.let { "${it.propertyPath}: ${it.message}" }
                ?: ex.message ?: "Validation failed"
        return ErrorResponse(code = "VALIDATION_ERROR", message = message)
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException): ErrorResponse =
        ErrorResponse(code = "VALIDATION_ERROR", message = "${ex.name}: invalid value '${ex.value}'")
}
