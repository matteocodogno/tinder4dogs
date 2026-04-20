package com.ai4dev.tinderfordogs.match.model

import java.util.UUID

data class Dog(
    val id: UUID,
    val name: String,
    val breed: String,
    val age: Int,
    val gender: String,
    val preferences: List<String> = emptyList(),
)
