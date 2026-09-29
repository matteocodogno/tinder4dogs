package com.ai4dev.tinder4dogs.dog

import org.springframework.data.jpa.repository.JpaRepository

interface DogRepository : JpaRepository<Dog, Long> {
    fun findByModesContaining(mode: Mode): List<Dog>
}
