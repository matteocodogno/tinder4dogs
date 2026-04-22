package com.ai4dev.tinderfordogs.match.service

import com.ai4dev.tinderfordogs.dogprofile.model.DogGender
import com.ai4dev.tinderfordogs.dogprofile.model.DogProfile
import com.ai4dev.tinderfordogs.dogprofile.model.DogSize
import com.ai4dev.tinderfordogs.dogprofile.repository.DogProfileRepository
import com.ai4dev.tinderfordogs.match.model.DogNotFoundException
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Optional
import java.util.UUID

class DogMatchServiceTest {
    private lateinit var repository: DogProfileRepository
    private lateinit var service: DogMatchService

    @BeforeEach
    fun setup() {
        repository = mockk()
        service = DogMatchService(repository)
    }

    private val sourceId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val candidateId1 = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val candidateId2 = UUID.fromString("00000000-0000-0000-0000-000000000003")

    private fun profile(
        id: UUID,
        breed: String = "Labrador",
        age: Int = 3,
        gender: DogGender = DogGender.MALE,
    ) = DogProfile(
        id = id,
        name = "Dog $id",
        breed = breed,
        size = DogSize.MEDIUM,
        age = age,
        gender = gender,
    )

    // --- Orchestration tests ---

    @Test
    fun `results are ordered by score descending`() {
        val source = profile(sourceId, breed = "Labrador", gender = DogGender.MALE)
        // candidate1: different breed, same gender → lower score
        val candidate1 = profile(candidateId1, breed = "Poodle", gender = DogGender.MALE)
        // candidate2: same breed, different gender → higher score
        val candidate2 = profile(candidateId2, breed = "Labrador", gender = DogGender.FEMALE)

        every { repository.findById(sourceId) } returns Optional.of(source)
        every { repository.findAll() } returns listOf(source, candidate1, candidate2)

        val result = service.findMatches(sourceId, 2)

        assertEquals(2, result.matches.size)
        assertEquals(candidateId2, result.matches[0].id)
        assertEquals(candidateId1, result.matches[1].id)
    }

    @Test
    fun `requesting dog is excluded from results`() {
        val source = profile(sourceId)

        every { repository.findById(sourceId) } returns Optional.of(source)
        every { repository.findAll() } returns listOf(source)

        val result = service.findMatches(sourceId, 10)

        assertTrue(result.matches.none { it.id == sourceId })
    }

    @Test
    fun `returns empty list when no other profiles exist`() {
        val source = profile(sourceId)
        every { repository.findById(sourceId) } returns Optional.of(source)
        every { repository.findAll() } returns listOf(source)

        val result = service.findMatches(sourceId, 5)

        assertEquals(0, result.matches.size)
    }

    @Test
    fun `throws DogNotFoundException for unknown dogId`() {
        every { repository.findById(sourceId) } returns Optional.empty()

        assertThrows<DogNotFoundException> { service.findMatches(sourceId, 1) }
    }

    @Test
    fun `id ascending is used as tiebreaker for equal scores`() {
        val source = profile(sourceId, breed = "Labrador", gender = DogGender.MALE)
        // Both candidates: same breed, same gender → identical score
        val candidate1 = profile(candidateId1, breed = "Labrador", gender = DogGender.MALE)
        val candidate2 = profile(candidateId2, breed = "Labrador", gender = DogGender.MALE)
        every { repository.findById(sourceId) } returns Optional.of(source)
        every { repository.findAll() } returns listOf(source, candidate2, candidate1)

        val result = service.findMatches(sourceId, 2)

        assertEquals(candidateId1, result.matches[0].id)
        assertEquals(candidateId2, result.matches[1].id)
    }

    // --- Algorithm tests ---

    @Test
    fun `compatibility score is always in 0_0 to 1_0`() {
        val source = profile(sourceId, breed = "Labrador", age = 0, gender = DogGender.MALE)
        val candidate = profile(candidateId1, breed = "Poodle", age = 30, gender = DogGender.MALE)
        every { repository.findById(sourceId) } returns Optional.of(source)
        every { repository.findAll() } returns listOf(source, candidate)

        val result = service.findMatches(sourceId, 1)

        assertTrue(result.matches[0].compatibilityScore in 0.0..1.0)
    }

    @Test
    fun `same-breed candidate scores higher than different-breed candidate`() {
        val source = profile(sourceId, breed = "Labrador", age = 3, gender = DogGender.MALE)
        val sameBreed = profile(candidateId1, breed = "Labrador", age = 3, gender = DogGender.MALE)
        val diffBreed = profile(candidateId2, breed = "Poodle", age = 3, gender = DogGender.MALE)
        every { repository.findById(sourceId) } returns Optional.of(source)
        every { repository.findAll() } returns listOf(source, sameBreed, diffBreed)

        val result = service.findMatches(sourceId, 2)

        assertTrue(result.matches[0].compatibilityScore > result.matches[1].compatibilityScore)
        assertEquals(candidateId1, result.matches[0].id)
    }

    @Test
    fun `different-gender candidate scores higher than same-gender candidate`() {
        val source = profile(sourceId, breed = "Labrador", age = 3, gender = DogGender.MALE)
        val diffGender = profile(candidateId1, breed = "Labrador", age = 3, gender = DogGender.FEMALE)
        val sameGender = profile(candidateId2, breed = "Labrador", age = 3, gender = DogGender.MALE)
        every { repository.findById(sourceId) } returns Optional.of(source)
        every { repository.findAll() } returns listOf(source, diffGender, sameGender)

        val result = service.findMatches(sourceId, 2)

        assertTrue(result.matches[0].compatibilityScore > result.matches[1].compatibilityScore)
        assertEquals(candidateId1, result.matches[0].id)
    }
}
