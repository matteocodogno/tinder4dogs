package com.ai4dev.tinderfordogs.match.model

import com.ai4dev.tinderfordogs.dogprofile.model.DogGender
import com.ai4dev.tinderfordogs.dogprofile.model.DogProfile
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

fun DogMatchEntry.toDogProfile(): DogProfile =
    DogProfile(
        id = id,
        name = name,
        breed = breed,
        size = size,
        age = age,
        gender = gender,
        bio = bio,
    )
