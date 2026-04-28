package com.ai4dev.tinderfordogs.agent.model

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

data class ChatRequest(
    @field:NotBlank
    @Schema(
        description = "Natural language request. Include the dog's name, a location, and optionally a preferred date.",
        example = "Find the best match for Biscuit near Central Park this weekend",
    )
    val message: String,
)
