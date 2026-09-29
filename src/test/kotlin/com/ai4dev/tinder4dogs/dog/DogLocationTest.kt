package com.ai4dev.tinder4dogs.dog

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DogLocationTest {

    private fun dog(location: Location? = null) = Dog(
        id = null,
        name = "Rex",
        breed = "Labrador",
        gender = Gender.MALE,
        age = 4,
        preferences = mutableSetOf("parks"),
        location = location,
    )

    @Test
    fun `two locations with the same coordinates are equal`() {
        assertThat(Location(latitude = 52.37, longitude = 4.89))
            .isEqualTo(Location(latitude = 52.37, longitude = 4.89))
    }

    @Test
    fun `a location tells its two coordinates apart`() {
        assertThat(Location(latitude = 1.0, longitude = 2.0))
            .isNotEqualTo(Location(latitude = 2.0, longitude = 1.0))
    }

    @Test
    fun `a dog has no location until one is given`() {
        assertThat(dog().location).isNull()
    }

    @Test
    fun `a dog carries the coordinate pair it was given as one unit`() {
        val amsterdam = Location(latitude = 52.37, longitude = 4.89)

        val located = dog(location = amsterdam)

        assertThat(located.location).isEqualTo(amsterdam)
        assertThat(located.location?.latitude).isEqualTo(52.37)
        assertThat(located.location?.longitude).isEqualTo(4.89)
    }

    @Test
    fun `a dog's location can be replaced by another one`() {
        val located = dog(location = Location(latitude = 52.37, longitude = 4.89))

        located.location = Location(latitude = 48.86, longitude = 2.35)

        assertThat(located.location).isEqualTo(Location(latitude = 48.86, longitude = 2.35))
    }
}
