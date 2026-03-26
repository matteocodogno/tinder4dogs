package com.ai4dev.tinderfordogs.support.model

data class CompatibilityResponse(
    val myDogBreed: String,
    val matchBreed: String,
    val advice: String,
    val sources: List<String>,
)
