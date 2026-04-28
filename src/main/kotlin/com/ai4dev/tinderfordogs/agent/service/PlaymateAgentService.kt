package com.ai4dev.tinderfordogs.agent.service

import com.ai4dev.tinderfordogs.agent.tools.PlaymateSearchTools
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Service
import java.time.LocalDate

private val logger = KotlinLogging.logger {}

@Service
class PlaymateAgentService(
    chatClientBuilder: ChatClient.Builder,
    private val playmateSearchTools: PlaymateSearchTools,
) {
    private val chatClient = chatClientBuilder.build()

    fun findPlaymate(prompt: String): String {
        val today = LocalDate.now()
        val systemPrompt =
            """
            You are a dog playdate assistant for Tinder for Dogs.
            Today's date is $today.

            When asked to find a playmate for a dog, follow these steps in order:
            1. Call searchDogProfiles with the city and distance from the request (default 10 km if not specified).
            2. Identify the user's dog from the prompt and exclude it from the candidates.
            3. For each candidate dog, call getMatchScore between the user's dog and that candidate.
            4. For each candidate with a score >= 50, call checkOwnerAvailability for the date mentioned in the request.
               Translate relative dates like "this weekend", "sabato", "domani" to absolute ISO dates relative to today ($today).
            5. Return a friendly, ranked recommendation of available dogs ordered by compatibility score.
               Include score, breed, age, and a brief reason for each recommendation.

            Respond in the same language as the user's request.
            """.trimIndent()

        logger.info { "Running playmate agent for prompt: $prompt" }

        return chatClient
            .prompt()
            .system(systemPrompt)
            .user(prompt)
            .tools(playmateSearchTools)
            .call()
            .content()
            ?: "Unable to generate a playmate recommendation at this time."
    }
}
