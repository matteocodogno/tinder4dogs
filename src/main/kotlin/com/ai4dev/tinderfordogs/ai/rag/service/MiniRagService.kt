package com.ai4dev.tinderfordogs.ai.rag.service

import com.ai4dev.tinderfordogs.ai.finetuning.service.ChatRequest
import com.ai4dev.tinderfordogs.ai.finetuning.service.EmbedRequest
import com.ai4dev.tinderfordogs.ai.finetuning.service.LiteLLMService
import com.ai4dev.tinderfordogs.ai.finetuning.service.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.stereotype.Service
import kotlin.math.sqrt

data class Chunk(
    val source: String,
    val text: String,
    var embedding: List<Double> = emptyList(),
)

@Service
class MiniRagService(
    private val llm: LiteLLMService,
) {
    companion object {
        private const val EMBED_MODEL = "local-embed"
        private const val CHAT_MODEL = "local-fast"
        private const val CHUNK_SIZE = 300
        private const val OVERLAP = 50
    }

    // ── Indexing ──────────────────────────────────────────────────────────────

    fun loadChunks(resourcePattern: String = "classpath:knowledge-base/*.md"): List<Chunk> {
        val resolver = PathMatchingResourcePatternResolver()
        return resolver.getResources(resourcePattern).flatMap { resource ->
            val content = resource.inputStream.bufferedReader().readText()
            splitIntoChunks(content).map { Chunk(source = resource.filename ?: "unknown", text = it) }
        }
    }

    suspend fun indexChunks(chunks: List<Chunk>): List<Chunk> =
        withContext(Dispatchers.IO) {
            chunks.map { chunk ->
                chunk.copy(
                    embedding =
                        llm
                            .embed(EmbedRequest(EMBED_MODEL, chunk.text))
                            .data
                            .first()
                            .embedding,
                )
            }
        }

    private fun splitIntoChunks(text: String): List<String> {
        val words = text.split("\\s+".toRegex())
        val result = mutableListOf<String>()
        var start = 0
        while (start < words.size) {
            result += words.subList(start, minOf(start + CHUNK_SIZE, words.size)).joinToString(" ")
            start += CHUNK_SIZE - OVERLAP
        }
        return result
    }

    // ── Retrieval ─────────────────────────────────────────────────────────────

    suspend fun retrieve(
        query: String,
        index: List<Chunk>,
        topK: Int = 2,
    ): List<Chunk> =
        withContext(Dispatchers.IO) {
            val queryVec =
                llm
                    .embed(EmbedRequest(EMBED_MODEL, query))
                    .data
                    .first()
                    .embedding
            index
                .sortedByDescending { cosineSimilarity(queryVec, it.embedding) }
                .take(topK)
        }

    private fun cosineSimilarity(
        a: List<Double>,
        b: List<Double>,
    ): Double {
        val dot = a.zip(b).sumOf { (x, y) -> x * y }
        val normA = sqrt(a.sumOf { it * it })
        val normB = sqrt(b.sumOf { it * it })
        return if (normA == 0.0 || normB == 0.0) 0.0 else dot / (normA * normB)
    }

    // ── Generation ────────────────────────────────────────────────────────────

    suspend fun answer(
        query: String,
        index: List<Chunk>,
    ): String {
        val retrieved = retrieve(query, index)
        val context = retrieved.joinToString("\n\n---\n\n") { "[From ${it.source}]:\n${it.text}" }
        val prompt    = """
            You are the Tinder for Dogs support assistant.
            Answer the user's question using ONLY the context below.
            Be friendly, concise, and always cite the source document.
            If the context does not contain enough information to answer, say so clearly
            and suggest the user contact support@tinder4dogs.com.

            Context:
            $context

            Question: $query
        """.trimIndent()
        return withContext(Dispatchers.IO) {
            llm
                .chat(ChatRequest(CHAT_MODEL, listOf(Message("user", prompt))))
                .choices
                .first()
                .message.content
        }
    }
}
