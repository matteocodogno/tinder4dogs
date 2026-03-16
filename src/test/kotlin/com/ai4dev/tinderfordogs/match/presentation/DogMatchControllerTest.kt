package com.ai4dev.tinderfordogs.match.presentation

import com.ai4dev.tinderfordogs.dogprofile.model.DogGender
import com.ai4dev.tinderfordogs.dogprofile.model.DogSize
import com.ai4dev.tinderfordogs.match.model.DogMatchEntry
import com.ai4dev.tinderfordogs.match.model.DogMatchListResponse
import com.ai4dev.tinderfordogs.match.model.DogNotFoundException
import com.ai4dev.tinderfordogs.match.service.DogMatchService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.util.UUID

@ExtendWith(SpringExtension::class)
@WebMvcTest(DogMatchController::class)
class DogMatchControllerTest {
    @MockkBean
    private lateinit var service: DogMatchService

    @Autowired
    private lateinit var mockMvc: MockMvc

    private val dogId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    private val sampleEntry =
        DogMatchEntry(
            id = UUID.fromString("00000000-0000-0000-0000-000000000002"),
            name = "Bella",
            breed = "Labrador",
            size = DogSize.LARGE,
            age = 2,
            gender = DogGender.FEMALE,
            bio = null,
            compatibilityScore = 0.75,
        )

    @Test
    fun `GET matches returns 200 with matches array`() {
        every { service.findMatches(any(), any()) } returns DogMatchListResponse(listOf(sampleEntry))

        mockMvc
            .get("/api/v1/dogs/$dogId/matches") {
                accept = MediaType.APPLICATION_JSON
            }.andExpect {
                status { isOk() }
                jsonPath("$.matches") { isArray() }
                jsonPath("$.matches[0].compatibilityScore") { value(0.75) }
            }
    }

    @Test
    fun `GET matches without limit defaults to 1`() {
        every { service.findMatches(eq(dogId), eq(1)) } returns DogMatchListResponse(listOf(sampleEntry))

        mockMvc
            .get("/api/v1/dogs/$dogId/matches") {
                accept = MediaType.APPLICATION_JSON
            }.andExpect {
                status { isOk() }
            }
    }

    @Test
    fun `GET matches with limit=0 returns 400 VALIDATION_ERROR`() {
        mockMvc
            .get("/api/v1/dogs/$dogId/matches?limit=0") {
                accept = MediaType.APPLICATION_JSON
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_ERROR") }
            }
    }

    @Test
    fun `GET matches with limit=11 returns 400 VALIDATION_ERROR`() {
        mockMvc
            .get("/api/v1/dogs/$dogId/matches?limit=11") {
                accept = MediaType.APPLICATION_JSON
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_ERROR") }
            }
    }

    @Test
    fun `GET matches with non-integer limit returns 400 VALIDATION_ERROR`() {
        mockMvc
            .get("/api/v1/dogs/$dogId/matches?limit=abc") {
                accept = MediaType.APPLICATION_JSON
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_ERROR") }
            }
    }

    @Test
    fun `GET matches with unknown dogId returns 404 DOG_NOT_FOUND`() {
        every { service.findMatches(any(), any()) } throws DogNotFoundException(dogId)

        mockMvc
            .get("/api/v1/dogs/$dogId/matches") {
                accept = MediaType.APPLICATION_JSON
            }.andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("DOG_NOT_FOUND") }
            }
    }
}
