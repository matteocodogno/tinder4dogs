package com.ai4dev.tinderfordogs.agent.tools

import com.ai4dev.tinderfordogs.agent.model.AvailabilityResult
import com.ai4dev.tinderfordogs.agent.model.DogProfileSummary
import com.ai4dev.tinderfordogs.agent.model.MatchScoreResult
import com.ai4dev.tinderfordogs.dogprofile.repository.DogProfileRepository
import com.ai4dev.tinderfordogs.match.service.CompatibilityScorer
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class PlaymateSearchTools(
    private val dogProfileRepository: DogProfileRepository,
) {
    @Tool(description = "Search for dog profiles near a city. Returns all available dog profiles. Location filtering is stubbed — all profiles are returned regardless of distance.")
    fun searchDogProfiles(
        @ToolParam(description = "The city to search in, e.g. 'Milano'") city: String,
        @ToolParam(description = "Maximum distance in kilometers from the city center") maxDistanceKm: Int,
    ): List<DogProfileSummary> {
        logger.info { "Searching dog profiles near $city within ${maxDistanceKm}km (location stubbed)" }
        return dogProfileRepository.findAll().map { dog ->
            DogProfileSummary(
                id = dog.id.toString(),
                name = dog.name,
                breed = dog.breed,
                age = dog.age,
                gender = dog.gender.name,
                size = dog.size.name,
            )
        }
    }

    @Tool(description = "Calculate the compatibility score between two dogs identified by name. Returns a score from 0 (incompatible) to 100 (perfect match).")
    fun getMatchScore(
        @ToolParam(description = "Name of the first dog (the one looking for a playmate)") dogAName: String,
        @ToolParam(description = "Name of the second dog (the candidate)") dogBName: String,
    ): MatchScoreResult {
        logger.info { "Calculating match score between $dogAName and $dogBName" }
        val all = dogProfileRepository.findAll()
        val dogA = all.firstOrNull { it.name.equals(dogAName, ignoreCase = true) }
            ?: return MatchScoreResult(dogAName, dogBName, 0, "Dog '$dogAName' not found in database")
        val dogB = all.firstOrNull { it.name.equals(dogBName, ignoreCase = true) }
            ?: return MatchScoreResult(dogAName, dogBName, 0, "Dog '$dogBName' not found in database")

        val score = (CompatibilityScorer.score(dogA, dogB) * 100).toInt()
        return MatchScoreResult(
            dogA = dogAName,
            dogB = dogBName,
            score = score,
            note = "Score based on age difference, breed match, gender compatibility",
        )
    }

    @Tool(description = "Check whether the owner of a dog is available for a playdate on a given date or weekend. Availability is currently simulated.")
    fun checkOwnerAvailability(
        @ToolParam(description = "Name of the dog whose owner availability should be checked") dogName: String,
        @ToolParam(description = "Date or date range in ISO format, e.g. '2026-05-02' or '2026-05-02/2026-05-04'") date: String,
    ): AvailabilityResult {
        logger.info { "Checking availability for $dogName on $date (stubbed)" }
        // Stubbed: deterministic based on dog name so results are consistent within a session
        val available = dogName.length % 2 == 0
        return AvailabilityResult(
            dogName = dogName,
            date = date,
            available = available,
            note = if (available) "Owner confirmed available" else "Owner not available for this date",
        )
    }
}
