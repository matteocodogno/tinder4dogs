package com.ai4dev.tinderfordogs.dogprofile.model

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class CreateDogProfileRequest(
    @field:NotBlank
    @field:Size(max = 100)
    val name: String?,
    @field:NotBlank
    @field:Size(max = 100)
    val breed: String?,
    @field:NotNull
    val size: DogSize?,
    @field:NotNull
    @field:Min(0)
    @field:Max(30)
    val age: Int?,
    @field:NotNull
    val gender: DogGender?,
    @field:Size(max = 500)
    val bio: String? = null,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
)
