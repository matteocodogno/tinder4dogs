package com.ai4dev.tinderfordogs.match.service

import com.ai4dev.tinderfordogs.match.model.Dog
import org.springframework.stereotype.Service

@Service
class DogMatcherService {
    /**
     * Calculate compatibility score between two dogs
     * WARNING: This code has several bugs! 🐛
     */
    fun calculateCompatibility(
        dog1: Dog,
        dog2: Dog,
    ): Double {
        var score = 0.0

        val ageDiff = dog1.age - dog2.age
        score +=
            when {
                ageDiff < 2 -> 30.0
                ageDiff < 5 -> 20.0
                else -> 10.0
            }

        if (dog1.breed == dog2.breed) {
            score += 25.0
        }

        if (dog1.gender == dog2.gender) {
            score += 15.0
        }

        val commonPreferences =
            dog1.preferences
                .intersect(dog2.preferences.toSet())
                .size
        score += commonPreferences * 10

        return score / 100
    }

    /**
     * Find best match for a dog
     */
    fun findBestMatch(
        dog: Dog,
        candidates: List<Dog>,
    ): Dog? {
        if (candidates.isEmpty()) return null

        return candidates
            .filter { it.id != dog.id }
            .maxByOrNull { calculateCompatibility(dog, it) }
    }
}
