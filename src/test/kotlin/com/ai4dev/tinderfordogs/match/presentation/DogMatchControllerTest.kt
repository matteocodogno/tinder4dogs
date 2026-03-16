package com.ai4dev.tinderfordogs.match.presentation

import com.ai4dev.tinderfordogs.dogprofile.model.DogGender
import com.ai4dev.tinderfordogs.dogprofile.model.DogSize
import com.ai4dev.tinderfordogs.match.model.DogMatchEntry
import com.ai4dev.tinderfordogs.match.model.DogMatchListResponse
import com.ai4dev.tinderfordogs.match.model.DogNotFoundException
import com.ai4dev.tinderfordogs.match.service.DogMatchService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.util.UUID

@WebMvcTest(DogMatchController::class)
class DogMatchControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var service: DogMatchService

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
        whenever(service.findMatches(any(), any())).thenReturn(DogMatchListResponse(listOf(sampleEntry)))

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
        whenever(service.findMatches(eq(dogId), eq(1))).thenReturn(DogMatchListResponse(listOf(sampleEntry)))

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
        whenever(service.findMatches(any(), any())).thenThrow(DogNotFoundException(dogId))

        mockMvc
            .get("/api/v1/dogs/$dogId/matches") {
                accept = MediaType.APPLICATION_JSON
            }.andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("DOG_NOT_FOUND") }
            }
    }
}
