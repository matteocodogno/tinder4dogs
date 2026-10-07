package com.ai4dev.tinder4dogs.dog

import org.springframework.data.jpa.repository.JpaRepository

interface DogRepository : JpaRepository<Dog, Long> {

    /** One dog per owner for v1, enforced by a unique index on the column. */
    fun findByOwnerSubject(ownerSubject: String): Dog?
}
