package com.ai4dev.tinderfordogs.dogprofile.model

import java.time.Instant
import java.util.UUID

data class DogProfileResponse(
    val id: UUID,
    val name: String,
    val breed: String,
    val size: DogSize,
    val age: Int,
    val gender: DogGender,
    val bio: String?,
    val createdAt: Instant,
)
