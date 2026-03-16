package com.ai4dev.tinderfordogs.match.service

import com.ai4dev.tinderfordogs.dogprofile.model.DogProfile

object CompatibilityScorer {
    private const val BREED_SCORE = 25.0
    private const val GENDER_SCORE = 15.0
    private const val MAX_PREFERENCES_SCORE = 30.0
    private const val COMMON_PREFERENCES_MULTIPLIER = 10.0
    private const val PERCENTAGE = 100
    private const val MAX_AGE_SCORE = 30.0
    private const val MIN_AGE_SCORE = 10.0

    fun score(
        source: DogProfile,
        candidate: DogProfile,
    ): Double =
        listOf(
            ::calculateAgeScore,
            ::calculateBreedScore,
            ::calculateGenderScore,
            ::calculatePreferencesScore,
        ).sumOf { it(source, candidate) }
            .div(PERCENTAGE)

    private fun calculateAgeScore(
        source: DogProfile,
        candidate: DogProfile,
    ): Double =
        (source.age - candidate.age).let { ageDiff ->
            when {
                ageDiff < 2 -> MAX_AGE_SCORE
                ageDiff < 5 -> 20.0
                else -> MIN_AGE_SCORE
            }
        }

    private fun calculateBreedScore(
        source: DogProfile,
        candidate: DogProfile,
    ): Double = if (source.breed == candidate.breed) BREED_SCORE else 0.0

    private fun calculateGenderScore(
        source: DogProfile,
        candidate: DogProfile,
    ): Double = if (source.gender != candidate.gender) GENDER_SCORE else 0.0

    private fun calculatePreferencesScore(
        source: DogProfile,
        candidate: DogProfile,
    ): Double = 0.0
}
