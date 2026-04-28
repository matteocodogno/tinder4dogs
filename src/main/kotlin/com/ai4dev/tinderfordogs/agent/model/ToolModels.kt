package com.ai4dev.tinderfordogs.agent.model

data class DogProfileSummary(
    val id: String,
    val name: String,
    val breed: String,
    val age: Int,
    val gender: String,
    val size: String,
)

data class MatchScoreResult(
    val dogA: String,
    val dogB: String,
    val score: Int,
    val note: String,
)

data class AvailabilityResult(
    val dogName: String,
    val date: String,
    val available: Boolean,
    val note: String,
)
