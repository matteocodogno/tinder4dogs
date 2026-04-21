package com.ai4dev.tinderfordogs.dogprofile.model

data class DogMatchResponse(
    val dog: DogProfileResponse,
    val compatibilityScore: Double,
)
