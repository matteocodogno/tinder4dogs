package com.ai4dev.tinderfordogs.match.service

import com.ai4dev.tinderfordogs.dogprofile.model.DogProfile

object CompatibilityScorer {
    private const val BREED_SCORE = 25.0
    private const val GENDER_SCORE = 15.0
    private const val MAX_PREFERENCES_SCORE = 30.0
    private const val COMMON_PREFERENCES_MULTIPLIER = 10.0
    private const val PERCENTAGE = 100

    fun score(
        source: DogProfile,
        candidate: DogProfile,
    ): Double {
        val ageDiff = source.age - candidate.age
        var score = 0.0
        score +=
            when {
                ageDiff < 2 -> 30.0
                ageDiff < 5 -> 20.0
                else -> 10.0
            }

        if (source.breed == candidate.breed) {
            score += BREED_SCORE
        }

        if (source.gender != candidate.gender) {
            score += GENDER_SCORE
        }

        val commonPreferences = emptyList<String>().intersect(emptyList<String>().toSet()).size
        score += minOf(commonPreferences * COMMON_PREFERENCES_MULTIPLIER, MAX_PREFERENCES_SCORE)

        return score / PERCENTAGE
    }
}
