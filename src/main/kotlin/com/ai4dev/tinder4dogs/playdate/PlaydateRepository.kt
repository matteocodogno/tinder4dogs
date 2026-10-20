package com.ai4dev.tinder4dogs.playdate

import org.springframework.data.jpa.repository.JpaRepository

interface PlaydateRepository : JpaRepository<Playdate, Long> {

    /** Every playdate the dog is part of, either side, newest first. */
    fun findByFromDogIdOrToDogIdOrderByIdDesc(fromDogId: Long, toDogId: Long): List<Playdate>
}
