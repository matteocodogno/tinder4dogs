package com.ai4dev.tinderfordogs.match.service

import com.ai4dev.tinderfordogs.dogprofile.repository.DogProfileRepository
import com.ai4dev.tinderfordogs.match.model.DogMatchEntry
import com.ai4dev.tinderfordogs.match.model.DogMatchListResponse
import com.ai4dev.tinderfordogs.match.model.DogNotFoundException
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DogMatchServiceTest {

    private lateinit var dogMatchService: DogMatchService
    private lateinit var dogProfileRepository: DogProfileRepository

    @BeforeEach
    fun setup() {
        dogProfileRepository = mockk()
        dogMatchService = DogMatchService(dogProfileRepository)
    }

    @Test
    fun `should return a list of dog matches when valid dog ID is provided`() {
        val dogId = UUID.randomUUID()
        val sourceDog = createDogProfile(dogId)
        val candidateDog1 = createDogProfile(UUID.randomUUID())
        val candidateDog2 = createDogProfile(UUID.randomUUID())

        every { dogProfileRepository.findById(dogId) } returns Optional.of(sourceDog)
        every { dogProfileRepository.findAll() } returns listOf(sourceDog, candidateDog1, candidateDog2)

        val result = dogMatchService.findMatches(dogId, 2)

        assertEquals(2, result.matches.size)
        assertEquals(candidateDog1.id, result.matches[0].id)
        assertEquals(candidateDog2.id, result.matches[1].id)
    }

    @Test
    fun `should throw DogNotFoundException when invalid dog ID is provided`() {
        val dogId = UUID.randomUUID()

        every { dogProfileRepository.findById(dogId) } returns Optional.empty()

        assertFailsWith<DogNotFoundException> {
            dogMatchService.findMatches(dogId, 1)
        }
    }

    @Test
    fun `should return empty list when no other dogs are available`() {
        val dogId = UUID.randomUUID()
        val sourceDog = createDogProfile(dogId)

        every { dogProfileRepository.findById(dogId) } returns Optional.of(sourceDog)
        every { dogProfileRepository.findAll() } returns listOf(sourceDog)

        val result = dogMatchService.findMatches(dogId, 5)

        assertEquals(0, result.matches.size)
    }

    @Test
    fun `should limit the number of matches to the specified limit`() {
        val dogId = UUID.randomUUID()
        val sourceDog = createDogProfile(dogId)
        val candidateDogs = List(10) { createDogProfile(UUID.randomUUID()) }

        every { dogProfileRepository.findById(dogId) } returns Optional.of(sourceDog)
        every { dogProfileRepository.findAll() } returns listOf(sourceDog) + candidateDogs

        val result = dogMatchService.findMatches(dogId, 5)

        assertEquals(5, result.matches.size)
    }

    private fun createDogProfile(id: UUID) = DogProfile(
        id = id,
        name = "Dog $id",
        breed = "Breed $id",
        size = "Medium",
        age = 5,
        gender = "Male",
        bio = "Bio of Dog $id"
    )
}
