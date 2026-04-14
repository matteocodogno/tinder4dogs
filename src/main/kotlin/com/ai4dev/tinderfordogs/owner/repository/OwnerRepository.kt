package com.ai4dev.tinderfordogs.owner.repository

import com.ai4dev.tinderfordogs.owner.model.Owner
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OwnerRepository : JpaRepository<Owner, UUID> {
    fun findByEmail(email: String): Owner?
}
