package com.ai4dev.tinderfordogs.ai.finetuning.model

data class FileUploadResponse(
    val id: String,
    val filename: String,
    val bytes: Long,
)
