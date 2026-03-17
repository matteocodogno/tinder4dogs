package com.ai4dev.tinderfordogs.match.service

import com.ai4dev.tinderfordogs.dogprofile.model.DogGender
import com.ai4dev.tinderfordogs.dogprofile.model.DogProfile
import com.ai4dev.tinderfordogs.dogprofile.model.DogSize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.util.UUID

/**
 * Unit tests for CompatibilityScorer targeting surviving and uncovered Pitest mutants.
 *
 * Surviving mutants addressed:
 *   M1  (line 16) – subtraction → addition on ageDiff
 *   M2  (line 20) – conditional boundary < 2  →  <= 2
 *   M3  (line 20) – first when-branch replaced with false
 *   M4  (line 21) – conditional boundary < 5  →  <= 5  [NO_COVERAGE]
 *   M5  (line 21) – second when-branch replaced with false [NO_COVERAGE]
 *
 * NOT addressed (equivalent mutants):
 *   M6  (line 34) – score += → score -=  on preferences contribution
 *   M7  (line 34) – multiplication → division on commonPreferences
 *   Reason: `commonPreferences` is hard-coded to 0 (empty-list intersection),
 *   so ±0.0 and 0/10.0 are indistinguishable from 0*10.0.
 *   Fix the production code to read from actual dog preferences first,
 *   then add an assertion like:
 *     assertEquals(0.50, score)  // ageDiff<2 (0.30) + 2 common prefs (0.20)
 */
class CompatibilityScorerTest {
    private fun profile(
        breed: String = "Labrador",
        age: Int = 3,
        gender: DogGender = DogGender.MALE,
    ) = DogProfile(
        id = UUID.randomUUID(),
        name = "Test Dog",
        breed = breed,
        size = DogSize.MEDIUM,
        age = age,
        gender = gender,
    )

    // ── Age scoring tiers ────────────────────────────────────────────────────

    /**
     * Kills M1 (subtraction → addition) and M3 (first branch → false).
     *
     * source.age=3, candidate.age=2 → real ageDiff = 1 → tier < 2 → 30.0 raw
     * M1 mutation: ageDiff = 3+2 = 5 → else tier → 10.0 raw
     * M3 mutation: first branch always false → falls to < 5 tier → 20.0 raw
     */
    @Test
    fun `age score contributes 30 raw points when age difference is less than 2`() {
        val source = profile(breed = "Poodle", age = 3, gender = DogGender.MALE)
        val candidate = profile(breed = "Labrador", age = 2, gender = DogGender.MALE)
        // ageDiff = 1, no breed match, same gender → only age score = 30.0 / 100
        assertEquals(0.30, CompatibilityScorer.score(source, candidate), 0.001)
    }

    /**
     * Kills M2 (boundary < 2 → <= 2).
     *
     * ageDiff = 2: original NOT < 2 → 20.0 raw; mutant 2 <= 2 → 30.0 raw
     */
    @Test
    fun `age score contributes 20 raw points when age difference is exactly 2`() {
        val source = profile(breed = "Poodle", age = 5, gender = DogGender.MALE)
        val candidate = profile(breed = "Labrador", age = 3, gender = DogGender.MALE)
        // ageDiff = 2, no breed match, same gender → 20.0 / 100
        assertEquals(0.20, CompatibilityScorer.score(source, candidate), 0.001)
    }

    /**
     * Kills M5 (second when-branch replaced with false) – provides coverage for
     * the ageDiff in [2, 5) tier that NO test was reaching before.
     *
     * ageDiff = 3: original IS < 5 → 20.0 raw; mutant always-false → else → 10.0 raw
     */
    @Test
    fun `age score contributes 20 raw points when age difference is between 2 and 4`() {
        val source = profile(breed = "Poodle", age = 6, gender = DogGender.MALE)
        val candidate = profile(breed = "Labrador", age = 3, gender = DogGender.MALE)
        // ageDiff = 3, no breed match, same gender → 20.0 / 100
        assertEquals(0.20, CompatibilityScorer.score(source, candidate), 0.001)
    }

    /**
     * Kills M4 (boundary < 5 → <= 5).
     *
     * ageDiff = 5: original NOT < 5 → else → 10.0 raw; mutant <= 5 → 20.0 raw
     */
    @Test
    fun `age score contributes 10 raw points when age difference is exactly 5`() {
        val source = profile(breed = "Poodle", age = 8, gender = DogGender.MALE)
        val candidate = profile(breed = "Labrador", age = 3, gender = DogGender.MALE)
        // ageDiff = 5, no breed match, same gender → 10.0 / 100
        assertEquals(0.10, CompatibilityScorer.score(source, candidate), 0.001)
    }

    @Test
    fun `age score contributes 10 raw points when age difference is greater than 5`() {
        val source = profile(breed = "Poodle", age = 10, gender = DogGender.MALE)
        val candidate = profile(breed = "Labrador", age = 3, gender = DogGender.MALE)
        // ageDiff = 7, no breed match, same gender → 10.0 / 100
        assertEquals(0.10, CompatibilityScorer.score(source, candidate), 0.001)
    }

    // ── Parameterised coverage of all three age tiers ────────────────────────

    /**
     * Verifies all three age-tier boundaries in a single parameterised test,
     * using different-breed, same-gender dogs so the only variable is age score.
     *
     * ageDiff | expected raw age score | expected normalised score
     *       0 |          30            | 0.30
     *       1 |          30            | 0.30
     *       2 |          20            | 0.20
     *       4 |          20            | 0.20
     *       5 |          10            | 0.10
     *      10 |          10            | 0.10
     */
    @ParameterizedTest(name = "ageDiff={0} → score={1}")
    @CsvSource(
        "0,  0.30",
        "1,  0.30",
        "2,  0.20",
        "4,  0.20",
        "5,  0.10",
        "10, 0.10",
    )
    fun `age tier boundaries produce correct normalised score`(
        ageDiff: Int,
        expected: Double,
    ) {
        val source = profile(breed = "Poodle", age = 10, gender = DogGender.MALE)
        val candidate = profile(breed = "Labrador", age = 10 - ageDiff, gender = DogGender.MALE)
        assertEquals(expected, CompatibilityScorer.score(source, candidate), 0.001)
    }

    // ── Breed and gender bonuses ──────────────────────────────────────────────

    @Test
    fun `same breed adds 25 raw points to score`() {
        val source = profile(breed = "Labrador", age = 3, gender = DogGender.MALE)
        val candidate = profile(breed = "Labrador", age = 2, gender = DogGender.MALE)
        // ageDiff=1 (30) + breed match (25) + same gender (0) → 55.0 / 100
        assertEquals(0.55, CompatibilityScorer.score(source, candidate), 0.001)
    }

    @Test
    fun `different gender adds 15 raw points to score`() {
        val source = profile(breed = "Poodle", age = 3, gender = DogGender.MALE)
        val candidate = profile(breed = "Labrador", age = 2, gender = DogGender.FEMALE)
        // ageDiff=1 (30) + no breed (0) + different gender (15) → 45.0 / 100
        assertEquals(0.45, CompatibilityScorer.score(source, candidate), 0.001)
    }

    @Test
    fun `all bonuses combined produce maximum possible score without preferences`() {
        val source = profile(breed = "Labrador", age = 3, gender = DogGender.MALE)
        val candidate = profile(breed = "Labrador", age = 2, gender = DogGender.FEMALE)
        // ageDiff=1 (30) + same breed (25) + different gender (15) → 70.0 / 100
        assertEquals(0.70, CompatibilityScorer.score(source, candidate), 0.001)
    }
}
