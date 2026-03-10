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

/**
 * REST controller responsible for handling breed compatibility requests within the Tinder for Dogs application.
 *
 * Exposes an endpoint under `/api/v1/matches` that accepts breed pairing requests and returns
 * AI-generated compatibility advice based on dog behaviour knowledge retrieved through a
 * Retrieval-Augmented Generation (RAG) pipeline.
 *
 * The controller maintains a lazily initialised in-memory vector index of knowledge base chunks
 * loaded via [MiniRagService]. This index is built once on first use and reused for all subsequent
 * requests. In a production environment, this in-memory index should be replaced with a proper
 * persistent vector store to support scalability and data durability.
 *
 * Breed compatibility analysis is delegated to [BreedCompatibilityService], which uses the index
 * to retrieve relevant context and generate a friendly, expert-informed compatibility summary
 * for the given breed pair.
 */
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
