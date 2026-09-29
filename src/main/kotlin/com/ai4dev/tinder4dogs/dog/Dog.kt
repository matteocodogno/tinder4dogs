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

/** What a dog is on the app for. A dog has at least one mode active. */
enum class Mode {
    PLAY,
    MATING,
}

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

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "dog_mode",
        joinColumns = [JoinColumn(name = "dog_id")],
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false)
    var modes: MutableSet<Mode> = mutableSetOf(),

    /** Self-declared, and only required once mating mode is activated. */
    @Column(name = "pedigree")
    var pedigree: String? = null,

    @Column(name = "neutered")
    var neutered: Boolean? = null,

    @Column(name = "health_status")
    var healthStatus: String? = null,
)
