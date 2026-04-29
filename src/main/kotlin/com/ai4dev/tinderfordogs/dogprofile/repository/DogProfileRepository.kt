package com.ai4dev.tinderfordogs.dogprofile.repository

import com.ai4dev.tinderfordogs.dogprofile.model.DogProfile
import com.ai4dev.tinderfordogs.owner.model.Owner
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DogProfileRepository : JpaRepository<DogProfile, UUID> {
    fun findByBreedContainingIgnoreCase(breed: String): List<DogProfile>

    fun findByNameIgnoreCase(name: String): DogProfile?

    fun findByOwner(owner: Owner): List<DogProfile>
}
