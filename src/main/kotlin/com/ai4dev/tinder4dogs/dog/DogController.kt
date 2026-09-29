package com.ai4dev.tinder4dogs.dog

import com.fasterxml.jackson.annotation.JsonInclude
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

private const val MIN_LATITUDE = "-90.0"
private const val MAX_LATITUDE = "90.0"
private const val MIN_LONGITUDE = "-180.0"
private const val MAX_LONGITUDE = "180.0"

/**
 * A complete coordinate pair as it arrives over HTTP. The ranges are checked
 * here, at the boundary, and the bounds are inclusive: a dog at the pole or on
 * the antimeridian is a legal dog. `@DecimalMin`/`@DecimalMax` rather than
 * `@Min`/`@Max`, which Bean Validation calls inappropriate for floating-point
 * types because of the precision loss they imply.
 */
data class LocationRequest(
    @field:DecimalMin(MIN_LATITUDE) @field:DecimalMax(MAX_LATITUDE) val latitude: Double,
    @field:DecimalMin(MIN_LONGITUDE) @field:DecimalMax(MAX_LONGITUDE) val longitude: Double,
) {
    fun toLocation() = Location(latitude = latitude, longitude = longitude)
}

data class LocationResponse(
    val latitude: Double,
    val longitude: Double,
) {
    companion object {
        fun of(location: Location) = LocationResponse(
            latitude = location.latitude,
            longitude = location.longitude,
        )
    }
}

data class DogRequest(
    @field:NotBlank val name: String,
    @field:NotBlank val breed: String,
    val gender: Gender,
    @field:Min(0) val age: Int,
    val preferences: Set<String> = emptySet(),
    @field:Valid val location: LocationRequest? = null,
)

data class DogResponse(
    val id: Long,
    val name: String,
    val breed: String,
    val gender: Gender,
    val age: Int,
    val preferences: Set<String>,
    @field:JsonInclude(JsonInclude.Include.NON_NULL) val location: LocationResponse? = null,
) {
    companion object {
        fun of(dog: Dog) = DogResponse(
            id = requireNonNullId(dog),
            name = dog.name,
            breed = dog.breed,
            gender = dog.gender,
            age = dog.age,
            preferences = dog.preferences.toSet(),
            location = dog.location?.let(LocationResponse::of),
        )

        private fun requireNonNullId(dog: Dog): Long =
            dog.id ?: error("a persisted dog always has an id")
    }
}

@RestController
@RequestMapping("/api/dogs")
class DogController(
    private val dogs: DogRepository,
) {

    @GetMapping
    fun all(): List<DogResponse> = dogs.findAll().map(DogResponse::of)

    @GetMapping("/{id}")
    fun byId(@PathVariable id: Long): ResponseEntity<DogResponse> =
        dogs.findById(id)
            .map { ResponseEntity.ok(DogResponse.of(it)) }
            .orElseGet { ResponseEntity.notFound().build() }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: DogRequest): DogResponse {
        val dog = Dog(
            name = request.name,
            breed = request.breed,
            gender = request.gender,
            age = request.age,
            preferences = request.preferences.toMutableSet(),
            location = request.location?.toLocation(),
        )
        return DogResponse.of(dogs.save(dog))
    }

    /**
     * Sets the dog's location, replacing any it already had -- setting and
     * replacing are the same operation. Clearing a location is not offered.
     */
    @PutMapping("/{id}/location")
    fun setLocation(
        @PathVariable id: Long,
        @Valid @RequestBody request: LocationRequest,
    ): ResponseEntity<DogResponse> =
        dogs.findById(id)
            .map { dog ->
                dog.location = request.toLocation()
                ResponseEntity.ok(DogResponse.of(dogs.save(dog)))
            }
            .orElseGet { ResponseEntity.notFound().build() }
}
