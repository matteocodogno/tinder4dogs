package com.ai4dev.tinderfordogs.dogprofile.repository

import com.ai4dev.tinderfordogs.dogprofile.model.DogProfile
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DogProfileRepository : JpaRepository<DogProfile, UUID>
