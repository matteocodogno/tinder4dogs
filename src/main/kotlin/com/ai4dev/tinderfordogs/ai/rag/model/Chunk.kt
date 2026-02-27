package com.ai4dev.tinderfordogs.ai.rag.model

data class Chunk(
    val source: String,
    val text: String,
    val embedding: List<Double> = emptyList(),
)
