package com.ai4dev.tinderfordogs.support.model

data class ModelComparison(
    val question: String,
    val fineTunedAnswer: String,
    val baseModelAnswer: String,
)
