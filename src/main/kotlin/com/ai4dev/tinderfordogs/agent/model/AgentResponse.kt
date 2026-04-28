package com.ai4dev.tinderfordogs.agent.model

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Result of the dog matching agent")
data class AgentResponse(
    @Schema(description = "Name of the best matching dog, or null if none found")
    val bestMatchName: String? = null,
    @Schema(description = "Compatibility score (0-100) between the reference dog and the best match")
    val matchScore: Int? = null,
    @Schema(description = "Whether the best match's owner is available on the requested date")
    val isAvailable: Boolean? = null,
    @Schema(description = "Agent's explanation of the recommendation")
    val reasoning: String = "",
)
