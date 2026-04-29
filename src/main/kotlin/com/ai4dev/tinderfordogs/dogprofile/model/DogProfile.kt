package com.ai4dev.tinderfordogs.dogprofile.model

import com.ai4dev.tinderfordogs.owner.model.Owner
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "dog_profiles")
class DogProfile(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,
    @Column(nullable = false, length = 100)
    val name: String,
    @Column(nullable = false, length = 100)
    val breed: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val size: DogSize,
    @Column(nullable = false)
    val age: Int,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val gender: DogGender,
    @Column(length = 500)
    val bio: String? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    val owner: Owner? = null,
    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.EPOCH,
    @Column(nullable = false)
    var updatedAt: Instant = Instant.EPOCH,
) {
    @PrePersist
    fun prePersist() {
        val now = Instant.now()
        createdAt = now
        updatedAt = now
    }
}
