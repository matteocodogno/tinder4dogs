package com.ai4dev.tinderfordogs.dogprofile.service

import com.ai4dev.tinderfordogs.dogprofile.model.CreateDogProfileRequest
import com.ai4dev.tinderfordogs.dogprofile.model.DogGender
import com.ai4dev.tinderfordogs.dogprofile.model.DogProfile
import com.ai4dev.tinderfordogs.dogprofile.model.DogSize
import com.ai4dev.tinderfordogs.dogprofile.repository.DogProfileRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.Instant
import java.util.UUID

class DogProfileServiceTest {
    private val repository: DogProfileRepository = mock(DogProfileRepository::class.java)
    private val service = DogProfileService(repository)

    @Test
    fun `create maps all fields to response`() {
        val id = UUID.randomUUID()
        val now = Instant.now()
        val saved =
            DogProfile(
                id = id,
                name = "Rex",
                breed = "Labrador",
                size = DogSize.LARGE,
                age = 3,
                gender = DogGender.MALE,
                bio = "Friendly dog",
                createdAt = now,
                updatedAt = now,
            )
        `when`(repository.save(any(DogProfile::class.java))).thenReturn(saved)

        val response =
            service.create(
                CreateDogProfileRequest(
                    name = "Rex",
                    breed = "Labrador",
                    size = DogSize.LARGE,
                    age = 3,
                    gender = DogGender.MALE,
                    bio = "Friendly dog",
                ),
            )

        assertEquals(id, response.id)
        assertEquals("Rex", response.name)
        assertEquals("Labrador", response.breed)
        assertEquals(DogSize.LARGE, response.size)
        assertEquals(3, response.age)
        assertEquals(DogGender.MALE, response.gender)
        assertEquals("Friendly dog", response.bio)
        assertEquals(now, response.createdAt)
    }

    @Test
    fun `create stores null bio when not provided`() {
        val now = Instant.now()
        val saved =
            DogProfile(
                id = UUID.randomUUID(),
                name = "Bella",
                breed = "Poodle",
                size = DogSize.SMALL,
                age = 1,
                gender = DogGender.FEMALE,
                bio = null,
                createdAt = now,
                updatedAt = now,
            )
        `when`(repository.save(any(DogProfile::class.java))).thenReturn(saved)

        val response =
            service.create(
                CreateDogProfileRequest(
                    name = "Bella",
                    breed = "Poodle",
                    size = DogSize.SMALL,
                    age = 1,
                    gender = DogGender.FEMALE,
                ),
            )

        assertNull(response.bio)
    }
}
