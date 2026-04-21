package com.ai4dev.tinderfordogs.match.service

import com.ai4dev.tinderfordogs.dogprofile.model.DogGender
import com.ai4dev.tinderfordogs.dogprofile.model.DogProfile
import com.ai4dev.tinderfordogs.dogprofile.model.DogSize
import com.ai4dev.tinderfordogs.dogprofile.repository.DogProfileRepository
import com.ai4dev.tinderfordogs.match.model.toDogProfile
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.collections.sortedDescending
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(DogMatchService::class)
class DogMatcherServiceIntegrationTest {
    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16")

        @DynamicPropertySource
        @JvmStatic
        fun registerDatasourceProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @Autowired
    private lateinit var dogProfileRepository: DogProfileRepository

    @Autowired
    private lateinit var dogMatcherService: DogMatchService

    @BeforeEach
    fun clearDatabase() {
        dogProfileRepository.deleteAll()
    }

    @Test
    fun shouldReturnTopThreeMatchesExcludingSubjectDogOrderedByCompatibilityDescending() {
        // Given — subject dog saved with name label "dog-42"; id is a generated UUID
        val subjectDog =
            dogProfileRepository.save(
                DogProfile(name = "dog-42", breed = "Labrador", size = DogSize.MEDIUM, age = 3, gender = DogGender.MALE),
            )

        // Four candidates with intentionally distinct compatibility scores against the subject:
        // Alpha  — same breed + same gender + age diff 0  → score 0.70
        // Beta   — same breed + diff gender + age diff 1  → score 0.55
        // Gamma  — diff breed + same gender + age diff 0  → score 0.45
        // Delta  — diff breed + diff gender + age diff 7  → score 0.10
        dogProfileRepository.saveAll(
            listOf(
                DogProfile(name = "Alpha", breed = "Labrador", size = DogSize.MEDIUM, age = 3, gender = DogGender.MALE),
                DogProfile(name = "Beta", breed = "Labrador", size = DogSize.LARGE, age = 4, gender = DogGender.FEMALE),
                DogProfile(name = "Gamma", breed = "Poodle", size = DogSize.SMALL, age = 3, gender = DogGender.MALE),
                DogProfile(name = "Delta", breed = "Beagle", size = DogSize.SMALL, age = 10, gender = DogGender.FEMALE),
            ),
        )

        // When
        val results = dogMatcherService.findMatches(subjectDog.id!!, 3)

        // Then — exactly 3 results
        assertEquals(3, results.matches.size, "Expected exactly 3 matches")

        // None of the results is the subject dog
        assertFalse(results.matches.any { it.id == subjectDog.id }, "Subject dog must not appear in results")

        // Results are ordered by compatibility score descending
        val scores = results.matches.map { CompatibilityScorer.score(subjectDog, it.toDogProfile()) }
        assertEquals(
            scores,
            scores.sortedDescending(),
            "Results must be ordered by compatibility score descending, got: $scores",
        )
    }
}
