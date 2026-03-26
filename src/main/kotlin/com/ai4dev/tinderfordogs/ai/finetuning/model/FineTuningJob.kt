package com.ai4dev.tinderfordogs.ai.finetuning.model

data class FineTuningJob(
    val id: String,
    val status: String,
    val model: String,
    val fineTunedModel: String?,
)
