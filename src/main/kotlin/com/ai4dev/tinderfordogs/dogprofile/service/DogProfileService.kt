package com.ai4dev.tinderfordogs.dogprofile.service

import com.ai4dev.tinderfordogs.dogprofile.model.CreateDogProfileRequest
import com.ai4dev.tinderfordogs.dogprofile.model.DogMatchResponse
import com.ai4dev.tinderfordogs.dogprofile.model.DogProfile
import com.ai4dev.tinderfordogs.dogprofile.model.DogProfileResponse
import com.ai4dev.tinderfordogs.dogprofile.repository.DogProfileRepository
import com.ai4dev.tinderfordogs.match.model.Dog
import com.ai4dev.tinderfordogs.match.service.DogMatcherService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Service
class DogProfileService(
    private val repository: DogProfileRepository,
    private val matcherService: DogMatcherService,
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
            )
        val saved = repository.save(entity)
        logger.info { "Dog profile created: id=${saved.id}, name=${saved.name}" }
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    fun findAll(): List<DogProfileResponse> = repository.findAll().map { it.toResponse() }

    @Transactional(readOnly = true)
    fun findMatches(
        id: UUID,
        limit: Int,
    ): List<DogMatchResponse> {
        val target =
            repository.findById(id).orElseThrow {
                NoSuchElementException("Dog profile not found: $id")
            }
        val targetDog = target.toDog()
        return repository
            .findAll()
            .filter { it.id != id }
            .map { candidate ->
                val score = matcherService.calculateCompatibility(targetDog, candidate.toDog())
                DogMatchResponse(dog = candidate.toResponse(), compatibilityScore = score)
            }.sortedByDescending { it.compatibilityScore }
            .take(limit)
    }

    private fun DogProfile.toDog() =
        Dog(
            id = id.hashCode().toLong(),
            name = name,
            breed = breed,
            age = age,
            gender = gender.name,
        )

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
