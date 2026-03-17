package com.ai4dev.tinderfordogs.support.service

import com.ai4dev.tinderfordogs.ai.common.service.ChatRequest
import com.ai4dev.tinderfordogs.ai.common.service.LiteLLMService
import com.ai4dev.tinderfordogs.ai.common.service.Message
import com.ai4dev.tinderfordogs.ai.rag.model.Chunk
import com.ai4dev.tinderfordogs.ai.rag.service.MiniRagService
import com.ai4dev.tinderfordogs.support.model.CompatibilityResponse
import org.springframework.stereotype.Service

/**
 * Service responsible for generating AI-powered compatibility advice between two dog breeds
 * within the Tinder for Dogs application.
 *
 * This service combines retrieval-augmented generation (RAG) with a language model to produce
 * contextually grounded, behaviour-focused compatibility summaries. Given two breed names and
 * a pre-built knowledge base index, it retrieves the most relevant knowledge chunks and
 * instructs the language model to summarise compatibility risks, special considerations,
 * and a practical first-meeting tip for the specific breed pair.
 *
 * The language model is constrained to use only the retrieved context when forming its response,
 * ensuring that the generated advice stays grounded in the provided knowledge base rather than
 * relying solely on the model's parametric knowledge.
 *
 * @param liteLLM The LiteLLM client used to send chat completion requests to the configured language model.
 * @param ragService The RAG service used to embed the compatibility query and retrieve the most
 * semantically relevant chunks from the knowledge base index.
 */
@Service
class BreedCompatibilityService(
    private val liteLLM: LiteLLMService,
    private val ragService: MiniRagService,
) {
    companion object {
        private const val CHAT_MODEL = "local-fast"
    }

    suspend fun generateCompatibilityAdvice(
        myBreed: String,
        matchBreed: String,
        index: List<Chunk>,
    ): CompatibilityResponse {
        val query = "compatibility between $myBreed and $matchBreed first meeting tips"
        val retrieved = ragService.retrieve(query, index)
        val context = retrieved.joinToString("\n\n---\n\n") { "[${it.source}]:\n${it.text}" }

        val prompt =
            """
            You are a dog behaviour expert inside the Tinder for Dogs app.
            A user's $myBreed is considering a meetup with a $matchBreed.
            Using ONLY the context below, give a short, friendly compatibility summary (3-5 sentences).
            Highlight any risks or special considerations for this specific breed pair.
            End with one practical first-meeting tip.
            If the context does not mention one of the breeds, say so honestly.

            Context:
            $context
            """.trimIndent()

        val answer =
            liteLLM
                .chat(
                    ChatRequest(
                        model = CHAT_MODEL,
                        messages = listOf(Message("user", prompt)),
                    ),
                ).choices
                .first()
                .message.content

        return CompatibilityResponse(
            myDogBreed = myBreed,
            matchBreed = matchBreed,
            advice = answer,
            sources = retrieved.map { it.source }.distinct(),
        )
    }
}
