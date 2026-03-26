package com.ai4dev.tinderfordogs.match.service

import com.ai4dev.tinderfordogs.match.model.Dog
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
    fun `should calculate high compatibility for dogs with similar attributes`() {
        // Given
        val dog1 = Dog(id = 1, name = "Buddy", breed = "Labrador", age = 5, gender = "Male", preferences = listOf("Running", "Swimming"))
        val dog2 = Dog(id = 2, name = "Max", breed = "Labrador", age = 4, gender = "Male", preferences = listOf("Running", "Playing"))

        // When
        val result = dogMatcherService.calculateCompatibility(dog1, dog2)

        // Then
        assertEquals(0.80, result, "Expected high compatibility score")
    }

    @Test
    fun `should calculate lower compatibility for dogs with different attributes`() {
        // Given
        val dog1 = Dog(id = 1, name = "Bella", breed = "Beagle", age = 10, gender = "Female", preferences = listOf("Sleeping"))
        val dog2 = Dog(id = 2, name = "Charlie", breed = "Poodle", age = 3, gender = "Male", preferences = listOf("Running"))

        // When
        val result = dogMatcherService.calculateCompatibility(dog1, dog2)

        // Then
        assertEquals(0.1, result, "Expected lower compatibility score")
    }

    @Test
    fun `should find best match from a list of candidates`() {
        // Given
        val dog =
            Dog(id = 1, name = "Rocky", breed = "Golden Retriever", age = 5, gender = "Male", preferences = listOf("Swimming", "Running"))
        val candidates =
            listOf(
                Dog(id = 2, name = "Luna", breed = "Labrador", age = 6, gender = "Female", preferences = listOf("Running", "Playing")),
                Dog(
                    id = 3,
                    name = "Daisy",
                    breed = "Golden Retriever",
                    age = 5,
                    gender = "Female",
                    preferences = listOf("Swimming", "Running"),
                ),
            )

        // When
        val result = dogMatcherService.findBestMatch(dog, candidates)

        // Then
        assertEquals(3, result?.id, "Expected the best match to be Dog with id 3")
    }

    @Test
    fun `should return null when no candidates are available`() {
        // Given
        val dog =
            Dog(id = 1, name = "Cooper", breed = "Golden Retriever", age = 5, gender = "Male", preferences = listOf("Swimming", "Running"))
        val candidates = emptyList<Dog>()

        // When
        val result = dogMatcherService.findBestMatch(dog, candidates)

        // Then
        assertNull(result, "Expected no match when no candidates are present")
    }

    @Test
    fun `should ignore the same dog in candidate list when finding best match`() {
        // Given
        val dog =
            Dog(id = 1, name = "Duke", breed = "Golden Retriever", age = 5, gender = "Male", preferences = listOf("Swimming", "Running"))
        val candidates =
            listOf(
                dog,
                Dog(id = 2, name = "Sadie", breed = "Labrador", age = 6, gender = "Female", preferences = listOf("Running", "Playing")),
            )

        // When
        val result = dogMatcherService.findBestMatch(dog, candidates)

        // Then
        assertEquals(2, result?.id, "Expected the best match to be Dog with id 2, ignoring the same dog")
    }
}
