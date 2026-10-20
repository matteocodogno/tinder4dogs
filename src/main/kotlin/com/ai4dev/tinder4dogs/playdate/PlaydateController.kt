package com.ai4dev.tinder4dogs.playdate

import com.ai4dev.tinder4dogs.dog.DogRepository
import com.ai4dev.tinder4dogs.profile.CurrentOwner
import java.time.LocalDate
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class PlaydateResponse(
    val id: Long,
    val fromDog: String,
    val toDog: String,
    val date: LocalDate,
    val park: Park,
    val status: PlaydateStatus,
)

/**
 * Plain REST, no model. The owner reads the truth here, whatever the agent
 * said in its reply.
 */
@RestController
@RequestMapping("/api/playdates")
class PlaydateController(
    private val playdates: PlaydateRepository,
    private val dogs: DogRepository,
) {

    /** Every playdate involving the current owner's dog, proposed by them or to them. */
    @GetMapping
    fun mine(@AuthenticationPrincipal principal: OidcUser?): List<PlaydateResponse> {
        val dog = dogs.findByOwnerSubject(CurrentOwner.subjectOf(principal)) ?: return emptyList()
        val dogId = dog.id ?: return emptyList()
        return toResponses(playdates.findByFromDogIdOrToDogIdOrderByIdDesc(dogId, dogId))
    }

    private fun toResponses(rows: List<Playdate>): List<PlaydateResponse> {
        val names = dogs.findAllById(rows.flatMap { listOf(it.fromDogId, it.toDogId) }.toSet())
            .associate { it.id to it.name }
        return rows.map {
            PlaydateResponse(
                id = it.id!!,
                fromDog = names[it.fromDogId] ?: "?",
                toDog = names[it.toDogId] ?: "?",
                date = it.scheduledOn,
                park = it.park,
                status = it.status,
            )
        }
    }
}
