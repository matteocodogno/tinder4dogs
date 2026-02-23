package com.ai4dev.tinderfordogs.ai.rag.presentation

import com.ai4dev.tinderfordogs.ai.rag.service.Chunk
import com.ai4dev.tinderfordogs.ai.rag.service.MiniRagService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class RagRequest(
    val query: String,
)

data class RagResult(
    val query: String,
    val answer: String,
    val sources: List<String>,
)

@RestController
@RequestMapping("/api/v1/rag")
class RagController(
    private val ragService: MiniRagService,
) {
    // In-memory index — reloaded on startup (extend with a proper store for production)
    private var index: List<Chunk> = emptyList()

    @PostMapping("/index")
    suspend fun buildIndex(): Map<String, Any> {
        val chunks = ragService.loadChunks()
        index = ragService.indexChunks(chunks)
        return mapOf("indexed" to index.size)
    }

    @PostMapping("/ask")
    suspend fun ask(
        @RequestBody req: RagRequest,
    ): RagResult {
        val retrieved = ragService.retrieve(req.query, index)
        val answer = ragService.answer(req.query, index)
        return RagResult(
            query = req.query,
            answer = answer,
            sources = retrieved.map { it.source }.distinct(),
        )
    }
}
