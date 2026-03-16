package com.ai4dev.tinderfordogs.match.model

import com.ai4dev.tinderfordogs.dogprofile.model.DogGender
import com.ai4dev.tinderfordogs.dogprofile.model.DogSize
import java.util.UUID

data class DogMatchEntry(
    val id: UUID,
    val name: String,
    val breed: String,
    val size: DogSize,
    val age: Int,
    val gender: DogGender,
    val bio: String?,
    val compatibilityScore: Double,
)
