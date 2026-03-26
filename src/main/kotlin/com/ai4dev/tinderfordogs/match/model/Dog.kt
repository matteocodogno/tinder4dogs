package com.ai4dev.tinderfordogs.match.model

data class Dog(
    val id: Long,
    val name: String,
    val breed: String,
    val age: Int,
    val gender: String,
    val preferences: List<String> = emptyList(),
)
