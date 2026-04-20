package com.ai4dev.tinderfordogs.match.presentation

import com.ai4dev.tinderfordogs.dogprofile.service.DogProfileService
import com.ai4dev.tinderfordogs.match.model.Dog
import com.ai4dev.tinderfordogs.match.service.DogMatcherService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class DogMatcherController(
    private val dogMatcherService: DogMatcherService,
    private val dogProfileService: DogProfileService,
) {
    @GetMapping("/api/dogs/{dogId}/matches")
    fun findBestMatch(
        @PathVariable dogId: UUID,
        @RequestParam limit: Int,
    ): List<Dog> {
        val allDogs =
            dogProfileService.findAll().map { profile ->
                Dog(
                    id = profile.id,
                    name = profile.name,
                    breed = profile.breed,
                    age = profile.age,
                    gender = profile.gender.name,
                )
            }
        val candidates = allDogs.filter { it.id != dogId }
        val dog = allDogs.find { it.id == dogId }

        if (dog == null) {
            throw RuntimeException("Dog not found")
        }

        val matches = dogMatcherService.findBestMatches(dog, candidates, limit)

        println("Matches: $matches")
        return matches
    }
}
