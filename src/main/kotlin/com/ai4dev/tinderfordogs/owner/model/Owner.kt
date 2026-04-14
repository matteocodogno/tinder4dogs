package com.ai4dev.tinderfordogs.owner.model

import jakarta.persistence.AttributeOverride
import jakarta.persistence.AttributeOverrides
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "owners")
class Owner(
    @Id
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false, unique = true, length = 255)
    val email: String,

    @Column(name = "password_hash", nullable = false, length = 60)
    val passwordHash: String,

    @Column(nullable = false, length = 100)
    val name: String,

    @Column(name = "photo_path", length = 500)
    val photoPath: String? = null,

    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "city", column = Column(name = "location_city", length = 100)),
        AttributeOverride(name = "country", column = Column(name = "location_country", length = 100)),
        AttributeOverride(name = "latitude", column = Column(name = "location_latitude")),
        AttributeOverride(name = "longitude", column = Column(name = "location_longitude")),
        AttributeOverride(name = "consentGiven", column = Column(name = "location_consent_given", nullable = false)),
    )
    val location: OwnerLocation = OwnerLocation(),

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val status: OwnerStatus = OwnerStatus.PENDING_VERIFICATION,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
