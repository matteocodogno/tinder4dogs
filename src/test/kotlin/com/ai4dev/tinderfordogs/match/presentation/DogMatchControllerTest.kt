package com.ai4dev.tinderfordogs.match.presentation

import com.ai4dev.tinderfordogs.dogprofile.model.DogGender
import com.ai4dev.tinderfordogs.dogprofile.model.DogSize
import com.ai4dev.tinderfordogs.match.model.DogMatchEntry
import com.ai4dev.tinderfordogs.match.model.DogMatchListResponse
import com.ai4dev.tinderfordogs.match.model.DogNotFoundException
import com.ai4dev.tinderfordogs.match.service.DogMatchService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.validation.ConstraintViolation
import jakarta.validation.ConstraintViolationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import java.util.UUID

class DogMatchControllerTest {
    private lateinit var service: DogMatchService
    private lateinit var controller: DogMatchController

    @BeforeEach
    fun setup() {
        service = mockk()
        controller = DogMatchController(service)
    }

    private val dogId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val matchId1 = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val matchId2 = UUID.fromString("00000000-0000-0000-0000-000000000003")

    // --- Happy path tests ---

    @Test
    fun `findMatches returns matches from service`() {
        val matches =
            listOf(
                DogMatchEntry(
                    id = matchId1,
                    name = "Buddy",
                    breed = "Labrador",
                    size = DogSize.MEDIUM,
                    age = 3,
                    gender = DogGender.MALE,
                    bio = "Friendly dog",
                    compatibilityScore = 0.95,
                ),
                DogMatchEntry(
                    id = matchId2,
                    name = "Max",
                    breed = "Golden Retriever",
                    size = DogSize.LARGE,
                    age = 2,
                    gender = DogGender.MALE,
                    bio = null,
                    compatibilityScore = 0.85,
                ),
            )
        val expectedResponse = DogMatchListResponse(matches)

        every { service.findMatches(dogId, 2) } returns expectedResponse

        val result = controller.findMatches(dogId, 2)

        assertEquals(expectedResponse, result)
        verify(exactly = 1) { service.findMatches(dogId, 2) }
    }

    @Test
    fun `findMatches uses default limit of 1 when not specified`() {
        val matches =
            listOf(
                DogMatchEntry(
                    id = matchId1,
                    name = "Buddy",
                    breed = "Labrador",
                    size = DogSize.MEDIUM,
                    age = 3,
                    gender = DogGender.MALE,
                    bio = null,
                    compatibilityScore = 0.95,
                ),
            )
        val expectedResponse = DogMatchListResponse(matches)

        every { service.findMatches(dogId, 1) } returns expectedResponse

        val result = controller.findMatches(dogId, 1)

        assertEquals(expectedResponse, result)
        verify(exactly = 1) { service.findMatches(dogId, 1) }
    }

    @Test
    fun `findMatches accepts maximum limit of 10`() {
        val expectedResponse = DogMatchListResponse(emptyList())

        every { service.findMatches(dogId, 10) } returns expectedResponse

        val result = controller.findMatches(dogId, 10)

        assertEquals(expectedResponse, result)
        verify(exactly = 1) { service.findMatches(dogId, 10) }
    }

    @Test
    fun `findMatches returns empty list when no matches found`() {
        val expectedResponse = DogMatchListResponse(emptyList())

        every { service.findMatches(dogId, 5) } returns expectedResponse

        val result = controller.findMatches(dogId, 5)

        assertEquals(0, result.matches.size)
        verify(exactly = 1) { service.findMatches(dogId, 5) }
    }

    // --- Exception handler tests ---

    @Test
    fun `handleNotFound returns 404 error response with message`() {
        val exception = DogNotFoundException(dogId)

        val response = controller.handleNotFound(exception)

        assertEquals("DOG_NOT_FOUND", response.code)
        assertEquals("Dog not found: $dogId", response.message)
    }

    @Test
    fun `handleConstraintViolation returns 400 error response with validation message`() {
        val violation = mockk<ConstraintViolation<*>>()
        every { violation.propertyPath.toString() } returns "limit"
        every { violation.message } returns "must be less than or equal to 10"

        val exception = mockk<ConstraintViolationException>()
        every { exception.constraintViolations } returns setOf(violation)

        val response = controller.handleConstraintViolation(exception)

        assertEquals("VALIDATION_ERROR", response.code)
        assertEquals("limit: must be less than or equal to 10", response.message)
    }

    @Test
    fun `handleConstraintViolation returns first violation when multiple violations exist`() {
        val violation1 = mockk<ConstraintViolation<*>>()
        every { violation1.propertyPath.toString() } returns "limit"
        every { violation1.message } returns "must be less than or equal to 10"

        val violation2 = mockk<ConstraintViolation<*>>()
        every { violation2.propertyPath.toString() } returns "dogId"
        every { violation2.message } returns "must not be null"

        val exception = mockk<ConstraintViolationException>()
        every { exception.constraintViolations } returns setOf(violation1, violation2)

        val response = controller.handleConstraintViolation(exception)

        assertEquals("VALIDATION_ERROR", response.code)
        assertEquals("limit: must be less than or equal to 10", response.message)
    }

    @Test
    fun `handleConstraintViolation returns default message when no violations present`() {
        val exception = mockk<ConstraintViolationException>()
        every { exception.constraintViolations } returns emptySet()
        every { exception.message } returns "Constraint violation occurred"

        val response = controller.handleConstraintViolation(exception)

        assertEquals("VALIDATION_ERROR", response.code)
        assertEquals("Constraint violation occurred", response.message)
    }

    @Test
    fun `handleConstraintViolation returns fallback message when exception message is null`() {
        val exception = mockk<ConstraintViolationException>()
        every { exception.constraintViolations } returns emptySet()
        every { exception.message } returns null

        val response = controller.handleConstraintViolation(exception)

        assertEquals("VALIDATION_ERROR", response.code)
        assertEquals("Validation failed", response.message)
    }

    @Test
    fun `handleTypeMismatch returns 400 error response with parameter name and value`() {
        val exception = mockk<MethodArgumentTypeMismatchException>()
        every { exception.name } returns "dogId"
        every { exception.value } returns "invalid-uuid"

        val response = controller.handleTypeMismatch(exception)

        assertEquals("VALIDATION_ERROR", response.code)
        assertEquals("dogId: invalid value 'invalid-uuid'", response.message)
    }

    @Test
    fun `handleTypeMismatch handles null value gracefully`() {
        val exception = mockk<MethodArgumentTypeMismatchException>()
        every { exception.name } returns "limit"
        every { exception.value } returns null

        val response = controller.handleTypeMismatch(exception)

        assertEquals("VALIDATION_ERROR", response.code)
        assertEquals("limit: invalid value 'null'", response.message)
    }
}
