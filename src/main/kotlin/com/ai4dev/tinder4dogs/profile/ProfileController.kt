package com.ai4dev.tinder4dogs.profile

import com.ai4dev.tinder4dogs.dog.Dog
import com.ai4dev.tinder4dogs.dog.DogRepository
import com.ai4dev.tinder4dogs.dog.DogSize
import com.ai4dev.tinder4dogs.dog.Gender
import com.ai4dev.tinder4dogs.dog.Temperament
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class ProfileRequest(
    val ownerName: String?,
    @field:NotBlank val name: String,
    @field:NotBlank val breed: String,
    val gender: Gender,
    val size: DogSize?,
    @field:Min(0) @field:Max(30) val age: Int,
    val temperament: Temperament?,
    val preferences: Set<String> = emptySet(),
)

data class ProfileResponse(
    val id: Long?,
    val ownerName: String?,
    val name: String,
    val breed: String,
    val gender: Gender,
    val size: DogSize?,
    val age: Int,
    val temperament: Temperament?,
    val preferences: Set<String>,
) {
    companion object {
        fun of(dog: Dog) = ProfileResponse(
            id = dog.id,
            ownerName = dog.ownerName,
            name = dog.name,
            breed = dog.breed,
            gender = dog.gender,
            size = dog.size,
            age = dog.age,
            temperament = dog.temperament,
            preferences = dog.preferences.toSet(),
        )
    }
}

/**
 * The signed-in owner's one dog.
 *
 * Note what is NOT here: length caps on the free-text fields, and any
 * validation beyond the field types and the age range. That is deliberate.
 * Module 12 has the room add those, after they have watched a model be talked
 * into producing values these fields will happily store.
 */
@RestController
@RequestMapping("/api/profile")
class ProfileController(
    private val dogs: DogRepository,
) {

    @GetMapping
    fun mine(@AuthenticationPrincipal principal: OidcUser?): ResponseEntity<ProfileResponse> {
        val dog = dogs.findByOwnerSubject(CurrentOwner.subjectOf(principal))
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(ProfileResponse.of(dog))
    }

    @PutMapping
    @Transactional
    fun save(
        @AuthenticationPrincipal principal: OidcUser?,
        @Valid @RequestBody request: ProfileRequest,
    ): ProfileResponse {
        val subject = CurrentOwner.subjectOf(principal)
        val dog = dogs.findByOwnerSubject(subject) ?: Dog(ownerSubject = subject)

        dog.ownerName = request.ownerName ?: CurrentOwner.displayNameOf(principal)
        dog.name = request.name
        dog.breed = request.breed
        dog.gender = request.gender
        dog.size = request.size
        dog.age = request.age
        dog.temperament = request.temperament
        dog.preferences = request.preferences.toMutableSet()

        return ProfileResponse.of(dogs.save(dog))
    }
}
