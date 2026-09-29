package com.ai4dev.tinder4dogs.dog

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embeddable
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table

enum class Gender {
    MALE,
    FEMALE,
}

/**
 * A coordinate pair, present or absent as one unit. Value equality only: no
 * domain logic here, and no range validation either -- coordinate ranges are
 * a rule of the request boundary, not of the stored value.
 */
@Embeddable
data class Location(
    @Column(name = "latitude")
    val latitude: Double,

    @Column(name = "longitude")
    val longitude: Double,
)

@Entity
@Table(name = "dog")
class Dog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false)
    var name: String = "",

    @Column(nullable = false)
    var breed: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var gender: Gender = Gender.FEMALE,

    @Column(nullable = false)
    var age: Int = 0,

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "dog_preference",
        joinColumns = [JoinColumn(name = "dog_id")],
    )
    @Column(name = "preference", nullable = false)
    var preferences: MutableSet<String> = mutableSetOf(),

    @Embedded
    var location: Location? = null,
)
