package com.ai4dev.tinderfordogs.dogprofile.presentation

import com.ai4dev.tinderfordogs.dogprofile.model.DogGender
import com.ai4dev.tinderfordogs.dogprofile.model.DogProfileResponse
import com.ai4dev.tinderfordogs.dogprofile.model.DogSize
import com.ai4dev.tinderfordogs.dogprofile.service.DogProfileService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.time.Instant
import java.util.UUID

@ExtendWith(SpringExtension::class)
@WebMvcTest(DogProfileController::class)
class DogProfileControllerTest {
    @MockkBean
    lateinit var service: DogProfileService

    @Autowired
    private lateinit var mockMvc: MockMvc

    private val sampleId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    private val sampleResponse =
        DogProfileResponse(
            id = sampleId,
            name = "Rex",
            breed = "Labrador",
            size = DogSize.LARGE,
            age = 3,
            gender = DogGender.MALE,
            bio = "Friendly dog",
            createdAt = Instant.parse("2026-03-12T10:00:00Z"),
        )

    @Test
    fun `POST with all required fields returns 201`() {
        every { service.create(any()) } returns sampleResponse

        mockMvc
            .post("/api/v1/dogs") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {"name":"Rex","breed":"Labrador","size":"LARGE","age":3,"gender":"MALE","bio":"Friendly dog"}
                    """.trimIndent()
            }.andExpect {
                status { isCreated() }
                jsonPath("$.id") { value(sampleId.toString()) }
                jsonPath("$.name") { value("Rex") }
                jsonPath("$.size") { value("LARGE") }
                jsonPath("$.gender") { value("MALE") }
            }
    }

    @Test
    fun `POST without bio returns 201`() {
        every { service.create(any()) } returns sampleResponse.copy(bio = null)

        mockMvc
            .post("/api/v1/dogs") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"Rex","breed":"Labrador","size":"LARGE","age":3,"gender":"MALE"}"""
            }.andExpect {
                status { isCreated() }
            }
    }

    @Test
    fun `POST with missing name returns 400 with VALIDATION_ERROR code`() {
        mockMvc
            .post("/api/v1/dogs") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"breed":"Labrador","size":"LARGE","age":3,"gender":"MALE"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_ERROR") }
            }
    }

    @Test
    fun `POST with invalid size enum returns 400`() {
        mockMvc
            .post("/api/v1/dogs") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"Rex","breed":"Labrador","size":"GIANT","age":3,"gender":"MALE"}"""
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `POST with age above 30 returns 400 with VALIDATION_ERROR code`() {
        mockMvc
            .post("/api/v1/dogs") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"Rex","breed":"Labrador","size":"LARGE","age":31,"gender":"MALE"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_ERROR") }
            }
    }

    @Test
    fun `POST with negative age returns 400 with VALIDATION_ERROR code`() {
        mockMvc
            .post("/api/v1/dogs") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"Rex","breed":"Labrador","size":"LARGE","age":-1,"gender":"MALE"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_ERROR") }
            }
    }
}
