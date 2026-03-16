package com.ai4dev.tinderfordogs.match.service

import com.ai4dev.tinderfordogs.dogprofile.model.DogProfile
import com.ai4dev.tinderfordogs.dogprofile.repository.DogProfileRepository
import com.ai4dev.tinderfordogs.match.model.DogMatchEntry
import com.ai4dev.tinderfordogs.match.model.DogMatchListResponse
import com.ai4dev.tinderfordogs.match.model.DogNotFoundException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.util.UUID

private val logger = KotlinLogging.logger {}

// Score budget: age(max 30) + breed(25) + gender(15) + preferences(max 30) = 100 max
// Keep this budget in sync with the / 100 divisor below
@Service
class DogMatchService(
    private val repository: DogProfileRepository,
) {
    fun findMatches(
        dogId: UUID,
        limit: Int,
    ): DogMatchListResponse {
        val source = repository.findById(dogId).orElseThrow { DogNotFoundException(dogId) }

        val matches =
            repository
                .findAll()
                .filter { it.id != dogId }
                .map { candidate ->
                    DogMatchEntry(
                        id = candidate.id!!,
                        name = candidate.name,
                        breed = candidate.breed,
                        size = candidate.size,
                        age = candidate.age,
                        gender = candidate.gender,
                        bio = candidate.bio,
                        compatibilityScore = calculateCompatibility(source, candidate),
                    )
                }.sortedWith(compareByDescending<DogMatchEntry> { it.compatibilityScore }.thenBy { it.id.toString() })
                .take(limit)

        logger.info { "Found ${matches.size} matches for dogId=$dogId (limit=$limit)" }
        return DogMatchListResponse(matches)
    }

    private fun calculateCompatibility(
        source: DogProfile,
        candidate: DogProfile,
    ): Double {
        var score = 0.0

        val ageDiff = source.age - candidate.age
        score +=
            when {
                ageDiff < 2 -> 30.0
                ageDiff < 5 -> 20.0
                else -> 10.0
            }

        if (source.breed == candidate.breed) {
            score += 25.0
        }

        if (source.gender != candidate.gender) {
            score += 15.0
        }

        val commonPreferences = emptyList<String>().intersect(emptyList<String>().toSet()).size
        score += minOf(commonPreferences * 10, 30)

        return score / 100
    }
}
