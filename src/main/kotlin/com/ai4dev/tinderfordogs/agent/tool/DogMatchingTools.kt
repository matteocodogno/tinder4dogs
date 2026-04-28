package com.ai4dev.tinderfordogs.agent.tool

import com.ai4dev.tinderfordogs.agent.model.DogSearchResult
import com.ai4dev.tinderfordogs.dogprofile.repository.DogProfileRepository
import com.ai4dev.tinderfordogs.match.service.CompatibilityScorer
import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Component
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@Component
class DogMatchingTools(
    private val repository: DogProfileRepository,
    private val agentContext: AgentContext,
) {
    @Tool(description = "Search for dog profiles near the reference location. Optionally filter by breed. Returns candidates sorted by distance in km.")
    fun searchDogProfiles(
        breed: String?,
        maxDistanceKm: Int = 10,
    ): List<DogSearchResult> =
        repository.findAll()
            .filter { it.name.lowercase() != agentContext.referenceDogName.lowercase() }
            .filter { breed == null || it.breed.equals(breed, ignoreCase = true) }
            .mapNotNull { dog ->
                val distKm = haversineKm(agentContext.referenceLatitude, agentContext.referenceLongitude, dog.latitude, dog.longitude)
                if (distKm <= maxDistanceKm) DogSearchResult(dog.id.toString(), dog.name, distKm) else null
            }
            .sortedBy { it.distanceKm }

    @Tool(description = "Calculate the compatibility score (0-100) between two dogs identified by name. A higher score means the dogs are more compatible as companions.")
    fun getMatchScore(
        dogA: String,
        dogB: String,
    ): Int {
        val a = repository.findByNameIgnoreCase(dogA) ?: return 0
        val b = repository.findByNameIgnoreCase(dogB) ?: return 0
        return (CompatibilityScorer.score(a, b) * 100).toInt()
    }

    @Tool(description = "Check whether the owner of a given dog (by ID) is available on the requested date (e.g. 'weekend', 'monday', 'tomorrow'). Returns true if the owner is available.")
    fun checkOwnerAvailability(
        dogId: String,
        date: String = "weekend",
    ): Boolean {
        // Deterministic stub: ~70% availability based on dogId+date hash.
        // Replace with a real availability table when the feature is built.
        return (dogId + date).hashCode() % 10 < 7
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return earthRadiusKm * 2 * asin(sqrt(a))
    }
}
