package com.ai4dev.tinderfordogs.match.service

import com.ai4dev.tinderfordogs.dogprofile.model.DogGender
import com.ai4dev.tinderfordogs.dogprofile.model.DogProfile
import com.ai4dev.tinderfordogs.dogprofile.model.DogSize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CompatibilityScorerTest {
    private fun profile(
        breed: String = "Labrador",
        age: Int = 3,
        gender: DogGender = DogGender.MALE,
    ) = DogProfile(
        name = "TestDog",
        breed = breed,
        size = DogSize.MEDIUM,
        age = age,
        gender = gender,
    )

    // --- Score bounds tests ---

    @Test
    fun `score is always between 0_0 and 1_0`() {
        val minProfile = profile(breed = "A", age = 0, gender = DogGender.MALE)
        val maxProfile = profile(breed = "B", age = 20, gender = DogGender.FEMALE)

        val score = CompatibilityScorer.score(minProfile, maxProfile)

        assertTrue(score in 0.0..1.0, "Score $score should be in [0.0, 1.0]")
    }

    @Test
    fun `minimum possible score is 0_10 when all factors differ maximally`() {
        val source = profile(breed = "Labrador", age = 0, gender = DogGender.MALE)
        val candidate = profile(breed = "Poodle", age = 20, gender = DogGender.MALE)

        val score = CompatibilityScorer.score(source, candidate)

        // age_diff >= 5 → 10 pts, different breed → 0 pts, same gender → 0 pts, no prefs → 0 pts = 10/100
        assertEquals(0.10, score, 0.001)
    }

    @Test
    fun `maximum possible score is 1_00 when all factors match optimally`() {
        val source = profile(breed = "Labrador", age = 3, gender = DogGender.MALE)
        val candidate = profile(breed = "Labrador", age = 3, gender = DogGender.FEMALE)

        val score = CompatibilityScorer.score(source, candidate)

        // age_diff < 2 → 30 pts, same breed → 25 pts, diff gender → 15 pts, prefs bug → 0 pts = 70/100
        assertEquals(0.70, score, 0.001)
    }

    // --- Age difference scoring tests ---

    @Test
    fun `age difference less than 2 years scores 30 points`() {
        val source = profile(breed = "Labrador", age = 5)
        val candidate1 = profile(breed = "Poodle", age = 5) // |5-5| = 0
        val candidate2 = profile(breed = "Poodle", age = 6) // |5-6| = 1
        val candidate3 = profile(breed = "Poodle", age = 4) // |5-4| = 1

        assertEquals(0.30, CompatibilityScorer.score(source, candidate1), 0.001)
        assertEquals(0.30, CompatibilityScorer.score(source, candidate2), 0.001)
        assertEquals(0.30, CompatibilityScorer.score(source, candidate3), 0.001)
    }

    @Test
    fun `age difference between 2 and 4 years scores 20 points`() {
        val source = profile(breed = "Labrador", age = 5)
        val candidate1 = profile(breed = "Poodle", age = 7) // |5-7| = 2
        val candidate2 = profile(breed = "Poodle", age = 8) // |5-8| = 3
        val candidate3 = profile(breed = "Poodle", age = 9) // |5-9| = 4

        assertEquals(0.20, CompatibilityScorer.score(source, candidate1), 0.001)
        assertEquals(0.20, CompatibilityScorer.score(source, candidate2), 0.001)
        assertEquals(0.20, CompatibilityScorer.score(source, candidate3), 0.001)
    }

    @Test
    fun `age difference of 5 or more years scores 10 points`() {
        val source = profile(breed = "Labrador", age = 5)
        val candidate1 = profile(breed = "Poodle", age = 10) // |5-10| = 5
        val candidate2 = profile(breed = "Poodle", age = 15) // |5-15| = 10
        val candidate3 = profile(breed = "Poodle", age = 0) // |5-0| = 5

        assertEquals(0.10, CompatibilityScorer.score(source, candidate1), 0.001)
        assertEquals(0.10, CompatibilityScorer.score(source, candidate2), 0.001)
        assertEquals(0.10, CompatibilityScorer.score(source, candidate3), 0.001)
    }

    @Test
    fun `age scoring is symmetric using absolute difference`() {
        val youngDog = profile(breed = "Labrador", age = 2)
        val oldDog = profile(breed = "Poodle", age = 10)

        // Both directions should give same score with absolute difference
        val scoreYoungToOld = CompatibilityScorer.score(youngDog, oldDog)
        val scoreOldToYoung = CompatibilityScorer.score(oldDog, youngDog)

        // |2 - 10| = 8 (>= 5) → 10 points, different breed → 0, same gender → 0 = 10/100
        assertEquals(0.10, scoreYoungToOld, 0.001)
        assertEquals(0.10, scoreOldToYoung, 0.001)
        assertEquals(scoreYoungToOld, scoreOldToYoung, 0.001)
    }

    // --- Breed scoring tests ---

    @Test
    fun `same breed adds 25 points to score`() {
        val source = profile(breed = "Labrador", age = 5, gender = DogGender.MALE)
        val sameBreed = profile(breed = "Labrador", age = 5, gender = DogGender.MALE)
        val diffBreed = profile(breed = "Poodle", age = 5, gender = DogGender.MALE)

        val scoreWithSameBreed = CompatibilityScorer.score(source, sameBreed)
        val scoreWithDiffBreed = CompatibilityScorer.score(source, diffBreed)

        assertEquals(0.25, scoreWithSameBreed - scoreWithDiffBreed, 0.001)
    }

    @Test
    fun `different breed adds 0 points to score`() {
        val source = profile(breed = "Labrador", age = 5)
        val candidate = profile(breed = "Beagle", age = 5)

        val score = CompatibilityScorer.score(source, candidate)

        // age < 2 → 30 pts, diff breed → 0 pts, same gender → 0 pts = 30/100
        assertEquals(0.30, score, 0.001)
    }

    // --- Gender scoring tests ---

    @Test
    fun `different gender adds 15 points to score`() {
        val source = profile(gender = DogGender.MALE, age = 5, breed = "Labrador")
        val diffGender = profile(gender = DogGender.FEMALE, age = 5, breed = "Labrador")
        val sameGender = profile(gender = DogGender.MALE, age = 5, breed = "Labrador")

        val scoreWithDiffGender = CompatibilityScorer.score(source, diffGender)
        val scoreWithSameGender = CompatibilityScorer.score(source, sameGender)

        assertEquals(0.15, scoreWithDiffGender - scoreWithSameGender, 0.001)
    }

    @Test
    fun `same gender adds 0 points to score`() {
        val source = profile(gender = DogGender.MALE, age = 5, breed = "Labrador")
        val candidate = profile(gender = DogGender.MALE, age = 5, breed = "Poodle")

        val score = CompatibilityScorer.score(source, candidate)

        // age < 2 → 30 pts, diff breed → 0 pts, same gender → 0 pts = 30/100
        assertEquals(0.30, score, 0.001)
    }

    // --- Preferences scoring tests (documents current bug) ---

    @Test
    fun `common preferences always score 0 due to empty list bug`() {
        val source = profile(age = 5, breed = "Labrador", gender = DogGender.MALE)
        val candidate = profile(age = 5, breed = "Labrador", gender = DogGender.MALE)

        val score = CompatibilityScorer.score(source, candidate)

        // age < 2 → 30 pts, same breed → 25 pts, same gender → 0 pts, prefs → 0 pts = 55/100
        assertEquals(0.55, score, 0.001)
    }

    // --- Combined scoring tests ---

    @Test
    fun `optimal match - same breed, different gender, close age`() {
        val source = profile(breed = "Labrador", age = 3, gender = DogGender.MALE)
        val candidate = profile(breed = "Labrador", age = 3, gender = DogGender.FEMALE)

        val score = CompatibilityScorer.score(source, candidate)

        // age 0 → 30, breed → 25, gender → 15, prefs → 0 = 70/100
        assertEquals(0.70, score, 0.001)
    }

    @Test
    fun `mixed match - same breed, same gender, moderate age difference`() {
        val source = profile(breed = "Labrador", age = 3, gender = DogGender.MALE)
        val candidate = profile(breed = "Labrador", age = 6, gender = DogGender.MALE)

        val score = CompatibilityScorer.score(source, candidate)

        // |3-6| = 3 → 20, breed → 25, gender → 0, prefs → 0 = 45/100
        assertEquals(0.45, score, 0.001)
    }

    @Test
    fun `poor match - different breed, same gender, large age difference`() {
        val source = profile(breed = "Labrador", age = 2, gender = DogGender.MALE)
        val candidate = profile(breed = "Poodle", age = 10, gender = DogGender.MALE)

        val score = CompatibilityScorer.score(source, candidate)

        // |2-10| = 8 (>=5) → 10, breed → 0, gender → 0, prefs → 0 = 10/100
        assertEquals(0.10, score, 0.001)
    }

    // --- Edge case tests ---

    @Test
    fun `exact same profile scores 0_55 (not perfect due to no gender bonus for same gender)`() {
        val profile = profile(breed = "Labrador", age = 5, gender = DogGender.MALE)

        val score = CompatibilityScorer.score(profile, profile)

        // age 0 → 30, breed → 25, same gender → 0, prefs → 0 = 55/100
        assertEquals(0.55, score, 0.001)
    }

    @Test
    fun `score with zero age for both dogs`() {
        val source = profile(age = 0)
        val candidate = profile(age = 0)

        val score = CompatibilityScorer.score(source, candidate)

        assertTrue(score >= 0.0)
    }

    @Test
    fun `score with maximum age difference`() {
        val source = profile(breed = "Labrador", age = 0)
        val candidate = profile(breed = "Poodle", age = 100)

        val score = CompatibilityScorer.score(source, candidate)

        // |0-100| = 100 (>=5) → 10 pts, different breed → 0, same gender → 0 = 10/100
        assertEquals(0.10, score, 0.001)
    }
}
