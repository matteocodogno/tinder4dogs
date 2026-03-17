package com.ai4dev.tinderfordogs.match.service

import com.ai4dev.tinderfordogs.dogprofile.model.DogGender
import com.ai4dev.tinderfordogs.dogprofile.model.DogProfile
import com.ai4dev.tinderfordogs.dogprofile.model.DogSize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class CompatibilityScorerTest {
    private fun profile(
        age: Int,
        breed: String = "Poodle",
        gender: DogGender = DogGender.MALE,
    ) = DogProfile(
        id = UUID.randomUUID(),
        name = "Dog",
        breed = breed,
        size = DogSize.MEDIUM,
        age = age,
        gender = gender,
    )

    // --- Age scoring (kills mutants 1, 3) ---

    @Test
    fun `ageDiff of 0 gives age score of 30`() {
        // Kills mutant 1 (subtraction→addition: 3+3=6 → else → 10 pts ≠ 30)
        // Kills mutant 3 (ageDiff-lt-2 → false: falls to lt-5 → 20 pts ≠ 30)
        val source = profile(age = 3)
        val candidate = profile(age = 3)
        // ageDiff=0 → 30 pts; same breed (+25); same gender (+0) → 55/100
        assertEquals(0.55, CompatibilityScorer.score(source, candidate), 0.001)
    }

    @Test
    fun `ageDiff of 1 gives age score of 30`() {
        // Kills mutant 1 (subtraction→addition: 4+3=7 → else → 10 pts ≠ 30)
        val source = profile(age = 4)
        val candidate = profile(age = 3)
        // ageDiff=1 → 30 pts; same breed (+25); same gender (+0) → 55/100
        assertEquals(0.55, CompatibilityScorer.score(source, candidate), 0.001)
    }

    // --- Boundary: ageDiff == 2 (kills mutant 2) ---

    @Test
    fun `ageDiff of exactly 2 gives age score of 20 not 30`() {
        // Kills mutant 2 (lt-2 → lte-2: would give 30 pts instead of 20)
        val source = profile(age = 5)
        val candidate = profile(age = 3)
        // ageDiff=2 → 20 pts; same breed (+25); same gender (+0) → 45/100
        assertEquals(0.45, CompatibilityScorer.score(source, candidate), 0.001)
    }

    // --- Middle age bracket (kills mutants 4, 5) ---

    @Test
    fun `ageDiff of 3 gives age score of 20`() {
        // Kills mutant 5 (lt-5 → false: would give else → 10 pts)
        val source = profile(age = 6)
        val candidate = profile(age = 3)
        // ageDiff=3 → 20 pts; same breed (+25); same gender (+0) → 45/100
        assertEquals(0.45, CompatibilityScorer.score(source, candidate), 0.001)
    }

    @Test
    fun `ageDiff of exactly 5 gives age score of 10 not 20`() {
        // Kills mutant 4 (lt-5 → lte-5: would give 20 pts instead of 10)
        val source = profile(age = 8)
        val candidate = profile(age = 3)
        // ageDiff=5 → 10 pts; same breed (+25); same gender (+0) → 35/100
        assertEquals(0.35, CompatibilityScorer.score(source, candidate), 0.001)
    }

    @Test
    fun `ageDiff of 6 gives age score of 10`() {
        val source = profile(age = 9)
        val candidate = profile(age = 3)
        // ageDiff=6 → 10 pts; same breed (+25); same gender (+0) → 35/100
        assertEquals(0.35, CompatibilityScorer.score(source, candidate), 0.001)
    }

    // --- Breed and gender contributions ---

    @Test
    fun `same breed adds 25 pts`() {
        val source = profile(age = 3, breed = "Labrador")
        val candidate = profile(age = 3, breed = "Labrador")
        // ageDiff=0 → 30; same breed → 25; same gender → 0 → 55/100
        assertEquals(0.55, CompatibilityScorer.score(source, candidate), 0.001)
    }

    @Test
    fun `different breed adds 0 pts`() {
        val source = profile(age = 3, breed = "Labrador")
        val candidate = profile(age = 3, breed = "Poodle")
        // ageDiff=0 → 30; diff breed → 0; same gender → 0 → 30/100
        assertEquals(0.30, CompatibilityScorer.score(source, candidate), 0.001)
    }

    @Test
    fun `different gender adds 15 pts`() {
        val source = profile(age = 3, gender = DogGender.MALE)
        val candidate = profile(age = 3, gender = DogGender.FEMALE)
        // ageDiff=0 → 30; same breed → 25; diff gender → 15 → 70/100
        assertEquals(0.70, CompatibilityScorer.score(source, candidate), 0.001)
    }

    @Test
    fun `same gender adds 0 pts`() {
        val source = profile(age = 3, gender = DogGender.MALE)
        val candidate = profile(age = 3, gender = DogGender.MALE)
        // ageDiff=0 → 30; same breed → 25; same gender → 0 → 55/100
        assertEquals(0.55, CompatibilityScorer.score(source, candidate), 0.001)
    }

    // --- Mutants 6 & 7: preferences addition/multiplication ---
    // These mutants survive because line 33 of CompatibilityScorer hardcodes
    // emptyList().intersect(emptyList()) — commonPreferences is always 0,
    // making += vs -= and * vs / on 0 equivalent.
    // These tests cannot be written until DogProfile gains a preferences field
    // and line 33 is fixed to use source.preferences and candidate.preferences.
}
