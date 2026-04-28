package com.ai4dev.tinderfordogs.agent.service

import com.ai4dev.tinderfordogs.agent.model.AgentResponse
import com.ai4dev.tinderfordogs.agent.tool.AgentContext
import com.ai4dev.tinderfordogs.agent.tool.DogMatchingTools
import tools.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Service

@Service
class DogMatchingAgentService(
    private val chatClient: ChatClient,
    private val tools: DogMatchingTools,
    private val agentContext: AgentContext,
    private val objectMapper: ObjectMapper,
) {
    private val log = KotlinLogging.logger {}

    fun chat(message: String): AgentResponse {
        log.info { "Agent chat: $message" }

        val parsed = parseRequest(message)
        agentContext.referenceDogName = parsed.dogName
        agentContext.referenceLatitude = parsed.latitude
        agentContext.referenceLongitude = parsed.longitude
        agentContext.preferredDate = parsed.date

        log.info { "Parsed request: dog=${parsed.dogName} at (${parsed.latitude},${parsed.longitude}) date=${parsed.date}" }

        val raw = chatClient.prompt()
            .system(AGENT_SYSTEM_PROMPT)
            .user(message)
            .tools(tools)
            .call()
            .content()

        return runCatching { objectMapper.readValue(raw, AgentResponse::class.java) }
            .getOrElse { AgentResponse(reasoning = raw ?: "No recommendation.") }
    }

    private fun parseRequest(message: String): ParsedRequest {
        val raw = chatClient.prompt()
            .system(EXTRACTION_SYSTEM_PROMPT)
            .user(message)
            .call()
            .content()

        return runCatching { objectMapper.readValue(raw, ParsedRequest::class.java) }
            .getOrElse { ParsedRequest(dogName = "", latitude = 0.0, longitude = 0.0, date = "weekend") }
    }

    private data class ParsedRequest(
        val dogName: String,
        val latitude: Double,
        val longitude: Double,
        val date: String,
    )

    companion object {
        private val EXTRACTION_SYSTEM_PROMPT =
            """
            Extract the following fields from the user's message and respond ONLY with a JSON object (no markdown, no extra text):
            {
              "dogName": "the name of the dog to find a match for",
              "latitude": <decimal latitude — resolve any place name or landmark to approximate coordinates>,
              "longitude": <decimal longitude>,
              "date": "the preferred date or 'weekend' if not specified"
            }
            """.trimIndent()

        private val AGENT_SYSTEM_PROMPT =
            """
            You are an expert dog matching assistant for the Tinder for Dogs platform.
            The reference dog's location and preferred date have already been resolved.

            Use the tools in this exact order and do NOT skip any step:
            1. Call searchDogProfiles to find nearby dogs.
            2. Call getMatchScore for EVERY dog returned in step 1.
            3. Call checkOwnerAvailability for the highest-scoring dog.
            4. Return the result.

            Respond ONLY with a JSON object (no markdown, no extra text):
            {"bestMatchName":"<name or null>","matchScore":<0-100 or null>,"isAvailable":<true/false or null>,"reasoning":"<brief explanation>"}
            """.trimIndent()
    }
}
