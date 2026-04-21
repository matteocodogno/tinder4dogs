package com.ai4dev.tinderfordogs.dogprofile.model

data class DogMatchResponse(
    val profile: DogProfileResponse,
    val compatibilityScore: Double
)