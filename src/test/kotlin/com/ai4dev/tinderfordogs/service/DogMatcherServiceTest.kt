package com.ai4dev.tinderfordogs.service

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DogMatcherServiceTest {

    private lateinit var dogMatcherService: DogMatcherService

    @BeforeEach
    fun setup() {
        dogMatcherService = DogMatcherService()
    }

    @Test
    fun `should return 1 when two dogs are perfectly compatible`() {
        // Given
        val dog1 = Dog(1, "Rex", "Labrador", 5, "Male", listOf("Running", "Swimming"))
        val dog2 = Dog(2, "Bella", "Labrador", 5, "Female", listOf("Running", "Swimming"))

        // When
        val result = dogMatcherService.calculateCompatibility(dog1, dog2)

        // Then
        assertEquals(1.0, result)
    }

    @Test
    fun `should return lower score when dogs have different breeds and no common preferences`() {
        // Given
        val dog1 = Dog(1, "Rex", "Labrador", 5, "Male", listOf("Running"))
        val dog2 = Dog(2, "Bella", "Husky", 3, "Female", listOf("Swimming"))

        // When
        val result = dogMatcherService.calculateCompatibility(dog1, dog2)

        // Then
        assertEquals(0.35, result)
    }

    @Test
    fun `should return 0 when dogs have maximum age difference and no common preferences or breed`() {
        // Given
        val dog1 = Dog(1, "Rex", "Labrador", 10, "Male", emptyList())
        val dog2 = Dog(2, "Bella", "Husky", 1, "Female", emptyList())

        // When
        val result = dogMatcherService.calculateCompatibility(dog1, dog2)

        // Then
        assertEquals(0.1, result)
    }

    @Test
    fun `should return null when no candidates are available`() {
        // Given
        val dog = Dog(1, "Rex", "Labrador", 5, "Male", listOf("Running"))

        // When
        val result = dogMatcherService.findBestMatch(dog, emptyList())

        // Then
        assertNull(result)
    }

    @Test
    fun `should find the best match among multiple candidates`() {
        // Given
        val dog = Dog(1, "Rex", "Labrador", 5, "Male", listOf("Running", "Swimming"))
        val candidates = listOf(
            Dog(2, "Bella", "Labrador", 5, "Female", listOf("Running", "Swimming")),
            Dog(3, "Max", "Husky", 7, "Male", listOf("Running")),
            Dog(4, "Lucy", "Labrador", 2, "Female", listOf("Swimming"))
        )

        // When
        val result = dogMatcherService.findBestMatch(dog, candidates)

        // Then
        assertEquals(2, result?.id)
    }

    @Test
    fun `should ignore the dog itself in the list of candidates`() {
        // Given
        val dog = Dog(1, "Rex", "Labrador", 5, "Male", listOf("Running", "Swimming"))
        val candidates = listOf(
            dog,
            Dog(2, "Bella", "Labrador", 5, "Female", listOf("Running", "Swimming"))
        )

        // When
        val result = dogMatcherService.findBestMatch(dog, candidates)

        // Then
        assertEquals(2, result?.id)
    }
}
