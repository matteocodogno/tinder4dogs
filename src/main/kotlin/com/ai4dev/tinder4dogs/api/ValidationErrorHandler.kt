package com.ai4dev.tinder4dogs.api

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Turns bean-validation failures into the one shape the single-page application
 * knows how to render: `{"errors": {"<field>": "<message>"}}`.
 *
 * Field names are passed through unchanged, so the keys the client looks up are
 * the property names on the request object.
 */
@RestControllerAdvice
class ValidationErrorHandler {

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun onInvalidBody(failure: MethodArgumentNotValidException): ResponseEntity<Map<String, Any>> {
        val errors = failure.bindingResult.fieldErrors.associate { error ->
            error.field to (error.defaultMessage ?: "is not valid")
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("errors" to errors))
    }
}
