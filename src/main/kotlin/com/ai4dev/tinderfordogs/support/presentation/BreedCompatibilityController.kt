package com.ai4dev.tinderfordogs.support.presentation

import com.ai4dev.tinderfordogs.ai.rag.model.Chunk
import com.ai4dev.tinderfordogs.ai.rag.service.MiniRagService
import com.ai4dev.tinderfordogs.support.model.CompatibilityRequest
import com.ai4dev.tinderfordogs.support.model.CompatibilityResponse
import com.ai4dev.tinderfordogs.support.service.BreedCompatibilityService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/matches")
class BreedCompatibilityController(
    private val breedCompatibility: BreedCompatibilityService,
    private val rag: MiniRagService,
) {
    // In production replace with a proper persistent vector store
    private val index: List<Chunk> by lazy { rag.loadChunks() }

    @PostMapping("/compatibility")
    suspend fun getCompatibilityAdvice(
        @RequestBody req: CompatibilityRequest,
    ): CompatibilityResponse = breedCompatibility.generateCompatibilityAdvice(req.myDogBreed, req.matchBreed, index)
}
