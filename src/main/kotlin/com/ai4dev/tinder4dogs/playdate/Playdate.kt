package com.ai4dev.tinder4dogs.playdate

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

/**
 * The parks the agent can propose. A closed list on purpose: a Kotlin enum
 * becomes an enum in the tool's JSON schema, so the model can only pick one of
 * these, and "Central Park" never reaches the code.
 */
enum class Park {
    PARCO_SEMPIONE,
    GIARDINI_MONTANELLI,
    PARCO_NORD,
    PARCO_LAMBRO,
}

enum class PlaydateStatus {
    /** Proposed by the agent, waiting for the owner. */
    DRAFT,

    /** Confirmed by the owner, through the app, never by the agent. */
    CONFIRMED,

    /** Cancelled. The row stays: it is the record. */
    CANCELLED,
}

@Entity
@Table(name = "playdate")
class Playdate(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "from_dog_id", nullable = false)
    var fromDogId: Long = 0,

    @Column(name = "to_dog_id", nullable = false)
    var toDogId: Long = 0,

    @Column(name = "scheduled_on", nullable = false)
    var scheduledOn: LocalDate = LocalDate.now(),

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    var park: Park = Park.PARCO_SEMPIONE,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: PlaydateStatus = PlaydateStatus.DRAFT,

    /** The owner subject of whoever asked the agent. Audit, and later, authority. */
    @Column(name = "created_by", nullable = false)
    var createdBy: String = "",

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "cancelled_at")
    var cancelledAt: Instant? = null,
)
