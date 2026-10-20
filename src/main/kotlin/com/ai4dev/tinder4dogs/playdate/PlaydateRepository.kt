package com.ai4dev.tinder4dogs.playdate

import java.time.Instant
import java.time.LocalDate
import org.springframework.data.jpa.repository.JpaRepository

interface PlaydateRepository : JpaRepository<Playdate, Long> {

    /** Every playdate the dog is part of, either side, newest first. */
    fun findByFromDogIdOrToDogIdOrderByIdDesc(fromDogId: Long, toDogId: Long): List<Playdate>

    /** The live (not cancelled) playdate for this pair on this day, if there is one. */
    fun findFirstByFromDogIdAndToDogIdAndScheduledOnAndStatusNot(
        fromDogId: Long,
        toDogId: Long,
        scheduledOn: LocalDate,
        status: PlaydateStatus,
    ): Playdate?

    /** How many playdates this owner has asked for since the given instant. */
    fun countByCreatedByAndCreatedAtGreaterThanEqual(createdBy: String, since: Instant): Long
}
