package com.ai4dev.tinder4dogs.dog

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
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
 * The closed vocabularies.
 *
 * They are enums because a closed list that is only described in prose is not
 * closed. From Module 12 onwards a model proposes values for these fields, and
 * the only reason a proposed value cannot be nonsense is that it has to survive
 * deserialisation into one of these constants.
 */
enum class DogSize {
    SMALL,
    MEDIUM,
    LARGE,
}

enum class Temperament {
    CALM,
    PLAYFUL,
    ANXIOUS,
    SOCIABLE,
    PROTECTIVE,
}

@Entity
@Table(name = "dog")
class Dog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    /**
     * The OIDC `sub` of the signed-in owner, or null for the seeded demo dogs,
     * which nobody owns and which exist to be searched against.
     */
    @Column(name = "owner_subject", length = 255)
    var ownerSubject: String? = null,

    @Column(name = "owner_name", length = 100)
    var ownerName: String? = null,

    @Column(nullable = false)
    var name: String = "",

    @Column(nullable = false)
    var breed: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var gender: Gender = Gender.FEMALE,

    @Enumerated(EnumType.STRING)
    @Column(name = "size", length = 10)
    var size: DogSize? = null,

    @Column(nullable = false)
    var age: Int = 0,

    @Enumerated(EnumType.STRING)
    @Column(name = "temperament", length = 20)
    var temperament: Temperament? = null,

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "dog_preference",
        joinColumns = [JoinColumn(name = "dog_id")],
    )
    @Column(name = "preference", nullable = false)
    var preferences: MutableSet<String> = mutableSetOf(),
)
