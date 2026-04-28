package com.ai4dev.tinderfordogs.agent.model

import jakarta.validation.constraints.NotBlank

data class PlaymateRequest(
    @field:NotBlank val prompt: String,
)
