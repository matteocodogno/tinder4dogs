package com.ai4dev.tinderfordogs.dogprofile.service

import com.ai4dev.tinderfordogs.dogprofile.model.CreateDogProfileRequest
import com.ai4dev.tinderfordogs.dogprofile.model.DogProfile
import com.ai4dev.tinderfordogs.dogprofile.model.DogProfileResponse
import com.ai4dev.tinderfordogs.dogprofile.repository.DogProfileRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class DogProfileService(
    private val repository: DogProfileRepository,
) {
    @Transactional
    fun create(request: CreateDogProfileRequest): DogProfileResponse {
        val entity =
            DogProfile(
                name = request.name!!,
                breed = request.breed!!,
                size = request.size!!,
                age = request.age!!,
                gender = request.gender!!,
                bio = request.bio,
                latitude = request.latitude,
                longitude = request.longitude,
            )
        val saved = repository.save(entity)
        logger.info { "Dog profile created: id=${saved.id}, name=${saved.name}" }
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    fun findAll(): List<DogProfileResponse> = repository.findAll().map { it.toResponse() }

    private fun DogProfile.toResponse() =
        DogProfileResponse(
            id = id!!,
            name = name,
            breed = breed,
            size = size,
            age = age,
            gender = gender,
            bio = bio,
            createdAt = createdAt,
        )
}
