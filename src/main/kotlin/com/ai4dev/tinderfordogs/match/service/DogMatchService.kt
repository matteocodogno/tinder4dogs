package com.ai4dev.tinderfordogs.match.service

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
                        compatibilityScore = CompatibilityScorer.score(source, candidate),
                    )
                }.sortedWith(
                    compareByDescending(DogMatchEntry::compatibilityScore)
                        .thenBy { it.id.toString() },
                ).take(limit)

        logger.info { "Found ${matches.size} matches for dogId=$dogId (limit=$limit)" }
        return DogMatchListResponse(matches)
    }
}
