package com.ai4dev.tinder4dogs.search

import com.ai4dev.tinder4dogs.dog.DogRepository
import com.ai4dev.tinder4dogs.dog.Gender
import com.ai4dev.tinder4dogs.profile.CurrentOwner
import com.ai4dev.tinder4dogs.profile.ProfileResponse
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Two filters, and the results in a random order.
 *
 * The random order is the point, not a placeholder: there is no match score in
 * this baseline and no ranking. `MatchScoreService` exists and is deliberately
 * NOT wired in here, because ranking is what Modules 13 and 14 add. A room that
 * can already see a relevance order spends the morning arguing about it.
 *
 * The filtering is done in Kotlin over every row rather than in SQL. With a
 * seeded handful of dogs that is the honest shape for a baseline, and it keeps
 * the query surface at zero. It is also the first thing Module 13 replaces.
 */
@RestController
class SearchController(
    private val dogs: DogRepository,
) {

    @GetMapping("/api/search")
    fun search(
        @AuthenticationPrincipal principal: OidcUser?,
        @RequestParam(required = false) breed: String?,
        @RequestParam(required = false) gender: Gender?,
    ): List<ProfileResponse> {
        val mySubject = CurrentOwner.subjectOf(principal)
        val wantedBreed = breed?.trim()?.takeIf { it.isNotEmpty() }

        return dogs.findAll()
            .asSequence()
            .filter { it.ownerSubject != mySubject }
            .filter { candidate -> wantedBreed == null || candidate.breed.equals(wantedBreed, ignoreCase = true) }
            .filter { candidate -> gender == null || candidate.gender == gender }
            .map(ProfileResponse::of)
            .toList()
            .shuffled()
    }
}
